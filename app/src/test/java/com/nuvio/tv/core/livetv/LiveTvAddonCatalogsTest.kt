package com.nuvio.tv.core.livetv

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LiveTvAddonCatalogsTest {
    @Test
    fun exposesEnabledTvAndChannelCatalogsAndProjectsPlayableItems() {
        val tvAddon = addon(
            id = "org.frostview.tv",
            name = "Frostview TV",
            baseUrl = "https://addons.example/config/private-key/manifest.json",
            catalogs = listOf(
                CatalogDescriptor(ContentType.TV, "tv", "channels", "Canais"),
                CatalogDescriptor(ContentType.MOVIE, "movie", "movies", "Filmes"),
            ),
            rawTypes = listOf("tv", "movie"),
        )
        val disabledChannelAddon = addon(
            id = "org.disabled",
            name = "Desativado",
            catalogs = listOf(CatalogDescriptor(ContentType.CHANNEL, "channel", "channels", "Canais")),
            enabled = false,
        )

        val catalog = installedLiveTvAddonCatalogs(listOf(tvAddon, disabledChannelAddon)).single()
        val channels = catalog.project(
            listOf(
                preview(id = "br:band", name = "Band"),
                preview(id = "br:record", name = "Record"),
                preview(id = "", name = "Invalid"),
            ),
        )

        assertEquals("tv", catalog.catalog.apiType)
        assertEquals(listOf("Band", "Record"), channels.map { it.displayName })
        assertTrue(channels.all { it.isPlayable })
        assertEquals("Frostview TV", channels.first().addonDisplayName)
        assertEquals(tvAddon.baseUrl, channels.first().addonBaseUrl)
        assertFalse(channels.first().id.contains("private-key"))
        val persistedChannel = Json.encodeToString(channels.first())
        assertFalse(persistedChannel.contains("addonBaseUrl"))
        assertFalse(persistedChannel.contains("private-key"))
    }

    @Test
    fun ignoresCatalogsThatAreNotLiveTvAndDoesNotReturnDisabledAddons() {
        val movieAddon = addon(
            id = "org.movies",
            name = "Movies",
            catalogs = listOf(CatalogDescriptor(ContentType.MOVIE, "movie", "popular", "Popular")),
            rawTypes = listOf("movie"),
        )
        val disabledAddon = addon(
            id = "org.disabled",
            name = "Disabled",
            catalogs = listOf(CatalogDescriptor(ContentType.CHANNEL, "channel", "channels", "Channels")),
            enabled = false,
        )

        assertTrue(installedLiveTvAddonCatalogs(listOf(movieAddon, disabledAddon)).isEmpty())
    }

    private fun addon(
        id: String,
        name: String,
        baseUrl: String = "https://addons.example/$id/manifest.json",
        catalogs: List<CatalogDescriptor>,
        rawTypes: List<String> = listOf("channel"),
        enabled: Boolean = true,
    ) = Addon(
        id = id,
        name = name,
        displayName = name,
        version = "1.0.0",
        description = null,
        logo = null,
        baseUrl = baseUrl,
        catalogs = catalogs,
        types = rawTypes.map(ContentType::fromString),
        rawTypes = rawTypes,
        resources = listOf(AddonResource("stream", rawTypes, null)),
        enabled = enabled,
    )

    private fun preview(id: String, name: String) = MetaPreview(
        id = id,
        type = ContentType.TV,
        rawType = "tv",
        name = name,
        poster = null,
        posterShape = PosterShape.SQUARE,
        background = null,
        logo = "https://images.example/$id.webp",
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList(),
    )
}
