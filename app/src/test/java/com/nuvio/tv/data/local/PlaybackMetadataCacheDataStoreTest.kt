package com.nuvio.tv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.storage.MediaCacheCategory
import com.nuvio.tv.core.storage.TvMediaCache
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackMetadataCacheDataStoreTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun entriesRemainScopedWhenProfilesSwitch() = runTest {
        val fixture = createFixture(backgroundScope)
        val cache = metadataCache()
        val streams = StreamLinkCacheDataStore(fixture.factory, fixture.profileManager, cache)
        val binge = BingeGroupCacheDataStore(fixture.factory, fixture.profileManager, cache)

        streams.save("movie", "https://example.test/movie.m3u8", "Perfil um", emptyMap())
        binge.save("series", "provider-one")

        fixture.activeProfile.value = 2
        assertNull(streams.getValid("movie", 60_000L))
        assertNull(binge.get("series"))
        streams.save("movie", "https://example.test/other.m3u8", "Perfil dois", emptyMap())
        binge.save("series", "provider-two")

        fixture.activeProfile.value = 1
        assertEquals("Perfil um", streams.getValid("movie", 60_000L)?.streamName)
        assertEquals("provider-one", binge.get("series"))
        fixture.activeProfile.value = 2
        assertEquals("Perfil dois", streams.getValid("movie", 60_000L)?.streamName)
        assertEquals("provider-two", binge.get("series"))
    }

    @Test
    fun cachedValuesRemainReadableAndUnchangedWhenWritesAreDisabled() = runTest {
        val fixture = createFixture(backgroundScope)
        var writesEnabled = true
        val cache = metadataCache { writesEnabled }
        val streams = StreamLinkCacheDataStore(fixture.factory, fixture.profileManager, cache)
        val binge = BingeGroupCacheDataStore(fixture.factory, fixture.profileManager, cache)

        streams.save("movie", "https://example.test/original.m3u8", "Original", emptyMap())
        binge.save("series", "original-group")
        writesEnabled = false

        streams.save("movie", "https://example.test/replacement.m3u8", "Replacement", emptyMap())
        binge.save("series", "replacement-group")

        assertEquals("Original", streams.getValid("movie", 60_000L)?.streamName)
        assertEquals("original-group", binge.get("series"))
    }

    @Test
    fun streamAndBingeStoresEnforceTheirPerProfileEntryCaps() = runTest {
        val fixture = createFixture(backgroundScope)
        val cache = metadataCache()
        val streams = StreamLinkCacheDataStore(fixture.factory, fixture.profileManager, cache)
        val binge = BingeGroupCacheDataStore(fixture.factory, fixture.profileManager, cache)

        repeat(130) { index ->
            streams.save("movie-$index", "https://example.test/$index.m3u8", "Stream $index", emptyMap())
        }
        repeat(258) { index -> binge.save("series-$index", "group-$index") }

        val streamCount = fixture.stores.getValue("1|stream_link_cache").data.first().asMap().keys
            .count { it.name.startsWith("stream_link_") }
        val bingeCount = fixture.stores.getValue("1|binge_group_cache").data.first().asMap().keys
            .count { it.name.startsWith("bg_") && !it.name.startsWith("bg_saved_at_") }

        assertEquals(128, streamCount)
        assertEquals(256, bingeCount)
        assertNotNull(streams.getValid("movie-129", 60_000L))
        assertEquals("group-257", binge.get("series-257"))
    }

    private fun createFixture(scope: kotlinx.coroutines.CoroutineScope): Fixture {
        val activeProfile = MutableStateFlow(1)
        val manager = mockk<ProfileManager> { every { activeProfileId } returns activeProfile }
        val stores = mutableMapOf<String, DataStore<Preferences>>()
        for (profileId in 1..2) {
            for (feature in listOf("stream_link_cache", "binge_group_cache")) {
                val profileSuffix = if (profileId == 1) "" else "_p$profileId"
                val file = directory.root.resolve("$feature$profileSuffix.preferences_pb")
                stores["$profileId|$feature"] = PreferenceDataStoreFactory.create(scope = scope) { file }
            }
        }
        val factory = mockk<ProfileDataStoreFactory> {
            every { get(any(), any()) } answers {
                stores.getValue("${firstArg<Int>()}|${secondArg<String>()}")
            }
        }
        return Fixture(factory, manager, activeProfile, stores)
    }

    private fun metadataCache(writesAllowed: () -> Boolean = { true }): TvMediaCache = mockk {
        every { writesEnabled } answers { writesAllowed() }
        every { cacheQuota(MediaCacheCategory.METADATA) } returns 24L * 1024 * 1024
        every { canWriteCacheEntry(MediaCacheCategory.METADATA, any()) } returns true
    }

    private data class Fixture(
        val factory: ProfileDataStoreFactory,
        val profileManager: ProfileManager,
        val activeProfile: MutableStateFlow<Int>,
        val stores: Map<String, DataStore<Preferences>>,
    )
}
