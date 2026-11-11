package com.nuvio.tv.data.local

import android.content.Context
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.TmdbCollectionFilters
import com.nuvio.tv.domain.model.TmdbCollectionMediaType
import com.nuvio.tv.domain.model.TmdbCollectionSource
import com.nuvio.tv.domain.model.TmdbCollectionSourceType
import io.mockk.mockk
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsDataStoreSourceMigrationTest {
    private val store = CollectionsDataStore(
        appContext = mockk<Context>(relaxed = true) {
        },
        factory = mockk<ProfileDataStoreFactory>(relaxed = true),
        profileManager = mockk<ProfileManager>(relaxed = true),
        authManager = mockk(relaxed = true),
        serverConfiguration = mockk(relaxed = true)
    )

    @Test
    fun `import converts legacy catalogSources to addon sources`() {
        val json = """
            [
              {
                "id": "collection",
                "title": "Legacy",
                "folders": [
                  {
                    "id": "folder",
                    "title": "Movies",
                    "catalogSources": [
                      {
                        "addonId": "addon",
                        "type": "movie",
                        "catalogId": "popular",
                        "genre": "Action"
                      }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val source = store.importFromJson(json).single().folders.single().sources.single()

        assertTrue(source is AddonCatalogCollectionSource)
        source as AddonCatalogCollectionSource
        assertEquals("addon", source.addonId)
        assertEquals("movie", source.type)
        assertEquals("popular", source.catalogId)
        assertEquals("Action", source.genre)
    }

    @Test
    fun `export includes provider aware tmdb sources`() {
        val collection = com.nuvio.tv.domain.model.Collection(
            id = "collection",
            title = "TMDB",
            folders = listOf(
                com.nuvio.tv.domain.model.CollectionFolder(
                    id = "folder",
                    title = "Marvel",
                    sources = listOf(
                        TmdbCollectionSource(
                            sourceType = TmdbCollectionSourceType.COMPANY,
                            title = "Marvel Studios",
                            tmdbId = 420,
                            mediaType = TmdbCollectionMediaType.MOVIE,
                            filters = TmdbCollectionFilters(
                                withoutGenres = "16",
                                withoutKeywords = "9715",
                                withoutCompanies = "2",
                                withoutWatchProviders = "8|337"
                            )
                        )
                    )
                )
            )
        )

        val json = store.exportToJson(listOf(collection))

        assertTrue(json.contains("\"sources\""))
        assertTrue(json.contains("\"provider\":\"tmdb\""))
        assertTrue(json.contains("\"tmdbSourceType\":\"COMPANY\""))
        assertTrue(json.contains("\"tmdbId\":420"))
        val source = store.importFromJson(json).single().folders.single().sources.single() as TmdbCollectionSource
        assertEquals("16", source.filters.withoutGenres)
        assertEquals("9715", source.filters.withoutKeywords)
        assertEquals("2", source.filters.withoutCompanies)
        assertEquals("8|337", source.filters.withoutWatchProviders)
    }

    @Test
    fun `retired provider sources are dropped on import without failing validation`() {
        val json = """
            [
              {
                "id": "collection",
                "title": "Legacy",
                "folders": [
                  {
                    "id": "folder",
                    "title": "Old Lists",
                    "sources": [
                      {
                        "provider": "legacy-list-provider",
                        "title": "Retired source",
                        "mediaType": "MOVIE"
                      }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val validation = store.validateCollectionsJson(json)
        val imported = store.importFromJson(json)

        assertTrue(validation.valid)
        assertTrue(imported.single().folders.single().sources.isEmpty())
    }

    @Test
    fun `import and export preserve folder hero video url`() {
        val collection = com.nuvio.tv.domain.model.Collection(
            id = "collection",
            title = "Videos",
            folders = listOf(
                com.nuvio.tv.domain.model.CollectionFolder(
                    id = "folder",
                    title = "Featured",
                    heroBackdropUrl = "https://example.com/backdrop.jpg",
                    heroVideoUrl = "https://example.com/hero.mp4",
                    sources = listOf(
                        AddonCatalogCollectionSource(
                            addonId = "addon",
                            type = "movie",
                            catalogId = "popular"
                        )
                    )
                )
            )
        )

        val json = store.exportToJson(listOf(collection))
        val folder = store.importFromJson(json).single().folders.single()

        assertTrue(json.contains("\"heroVideoUrl\":\"https://example.com/hero.mp4\""))
        assertEquals("https://example.com/hero.mp4", folder.heroVideoUrl)
    }
}
