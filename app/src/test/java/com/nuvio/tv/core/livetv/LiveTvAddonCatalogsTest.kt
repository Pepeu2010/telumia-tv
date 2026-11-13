package com.nuvio.tv.core.livetv

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvAddonCatalogsTest {
    @Test
    fun loadsEnabledChannelAndTvCatalogsAndKeepsPrivateAddonUrlOutOfIdsAndPersistence() {
        val addon = addon(
            catalogs = listOf(
                CatalogDescriptor(ContentType.CHANNEL, "channel", "channels", "Channels"),
                CatalogDescriptor(ContentType.TV, "tv", "live", "Live"),
                CatalogDescriptor(ContentType.MOVIE, "movie", "movies", "Movies"),
            ),
            baseUrl = "https://addons.example/config/private-key/manifest.json",
            rawTypes = listOf("channel", "tv", "movie"),
        )
        val disabledAddon = addon(
            id = "org.disabled",
            catalogs = listOf(CatalogDescriptor(ContentType.CHANNEL, "channel", "channels", "Channels")),
            enabled = false,
        )

        val catalogs = installedLiveTvAddonCatalogs(listOf(addon, disabledAddon))
        val channel = catalogs.first { it.catalog.apiType == "channel" }
            .project(listOf(preview("br:band", "Band"))).single()

        assertEquals(listOf("channel", "tv"), catalogs.map { it.catalog.apiType })
        assertTrue(channel.isPlayable)
        assertEquals("Test addon", channel.addonDisplayName)
        assertEquals(addon.baseUrl, channel.addonBaseUrl)
        assertFalse(channel.id.contains("private-key"))
        val persistedChannel = Json.encodeToString(channel)
        assertFalse(persistedChannel.contains("addonBaseUrl"))
        assertFalse(persistedChannel.contains("private-key"))
    }

    @Test
    fun ignoresDisabledAddonsAndNonTvCatalogs() {
        val addon = addon(
            catalogs = listOf(CatalogDescriptor(ContentType.MOVIE, "movie", "popular", "Popular")),
            rawTypes = listOf("movie"),
        )
        val disabledAddon = addon(
            id = "org.disabled",
            catalogs = listOf(CatalogDescriptor(ContentType.CHANNEL, "channel", "channels", "Channels")),
            enabled = false,
        )

        assertTrue(installedLiveTvAddonCatalogs(listOf(addon, disabledAddon)).isEmpty())
    }

    @Test
    fun categoriesUseOnlyExplicitMetadataAndSameChannelFromDifferentAddonsStaysDistinct() {
        val catalog = CatalogDescriptor(ContentType.CHANNEL, "channel", "channels", "Channels")
        val first = installedLiveTvAddonCatalogs(listOf(addon(catalogs = listOf(catalog)))).single()
        val second = installedLiveTvAddonCatalogs(listOf(addon(id = "org.other", catalogs = listOf(catalog)))).single()
        val sports = preview("match", "Canal Notícias").copy(genres = listOf("Esportes"))
        val firstChannel = first.project(listOf(sports)).single()
        val secondChannel = second.project(listOf(sports)).single()

        assertEquals("SPORTS", firstChannel.groupName)
        assertFalse(firstChannel.id == secondChannel.id)
        assertEquals(LiveTvChannelCategory.NEWS, LiveTvChannelCategory.fromGroup("Jornalismo"))
        assertEquals(LiveTvChannelCategory.OTHER, LiveTvChannelCategory.fromGenres(listOf("Lifestyle")))
        assertEquals(LiveTvChannelCategory.UNCATEGORIZED, LiveTvChannelCategory.fromGenres(emptyList()))
        assertEquals(LiveTvChannelCategory.UNCATEGORIZED,
            LiveTvChannelCategory.fromGenres(listOf("Esportes", "Jornalismo")))
    }

    private fun addon(
        id: String = "org.test.channels",
        name: String = "Test addon",
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
        type = ContentType.CHANNEL,
        rawType = "channel",
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
