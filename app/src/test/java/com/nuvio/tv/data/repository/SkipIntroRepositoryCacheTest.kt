package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.core.runtime.DeviceCapabilities
import com.nuvio.tv.core.runtime.DeviceResourceTier
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.AnimeSkipSettingsDataStore
import com.nuvio.tv.data.remote.api.AniSkipApi
import com.nuvio.tv.data.remote.api.AniSkipResponse
import com.nuvio.tv.data.remote.api.AnimeSkipApi
import com.nuvio.tv.data.remote.api.AnimeSkipData
import com.nuvio.tv.data.remote.api.AnimeSkipResponse
import com.nuvio.tv.data.remote.api.IntroDbApi
import com.nuvio.tv.data.remote.api.IntroDbSegmentsResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class SkipIntroRepositoryCacheTest {
    @Test
    fun `tier policy scales both retained result caches`() {
        val low = SkipIntroCacheLimits.forTier(DeviceResourceTier.LOW_RAM)
        val unknown = SkipIntroCacheLimits.forTier(DeviceResourceTier.UNKNOWN)
        val balanced = SkipIntroCacheLimits.forTier(DeviceResourceTier.BALANCED)
        val high = SkipIntroCacheLimits.forTier(DeviceResourceTier.HIGH_CAPACITY)

        assertEquals(low, unknown)
        assertTrue(low.intervals < balanced.intervals)
        assertTrue(low.animeSkipShowIds < balanced.animeSkipShowIds)
        assertTrue(balanced.intervals < high.intervals)
        assertTrue(balanced.animeSkipShowIds < high.animeSkipShowIds)
    }

    @Test
    fun `interval and show id caches bound entries and evict least recently used`() {
        val cache = SkipIntroCache(
            limits = SkipIntroCacheLimits(
                intervals = 2,
                animeSkipShowIds = 2,
                intervalTtlMillis = 100,
                animeSkipShowIdTtlMillis = 100,
            ),
            nowMillis = { 1_000L },
        )

        cache.putIntervals("a", emptyList()) // valid negative result
        cache.putIntervals("b", emptyList())
        assertNotNull(cache.getIntervals("a")) // refresh a's LRU position
        cache.putIntervals("c", emptyList())

        cache.putAnimeSkipShowId("a", "show-a")
        cache.putAnimeSkipShowId("b", "__none__") // cached negative lookup
        assertEquals("show-a", cache.getAnimeSkipShowId("a"))
        cache.putAnimeSkipShowId("c", "show-c")

        assertEquals(2, cache.intervalSize)
        assertNotNull(cache.getIntervals("a"))
        assertNull(cache.getIntervals("b"))
        assertNotNull(cache.getIntervals("c"))
        assertEquals(2, cache.animeSkipShowIdSize)
        assertEquals("show-a", cache.getAnimeSkipShowId("a"))
        assertNull(cache.getAnimeSkipShowId("b"))
        assertEquals("show-c", cache.getAnimeSkipShowId("c"))
    }

    @Test
    fun `negative entries expire from both caches`() {
        var now = 10L
        val cache = SkipIntroCache(
            limits = SkipIntroCacheLimits(
                intervals = 1,
                animeSkipShowIds = 1,
                intervalTtlMillis = 20,
                animeSkipShowIdTtlMillis = 40,
            ),
            nowMillis = { now },
        )

        cache.putIntervals("negative", emptyList())
        cache.putAnimeSkipShowId("negative", "__none__")
        now = 30L
        assertNotNull(cache.getIntervals("negative"))
        assertNotNull(cache.getAnimeSkipShowId("negative"))
        now = 51L
        assertNull(cache.getIntervals("negative"))
        assertNull(cache.getAnimeSkipShowId("negative"))
    }

    @Test
    fun `transient AniSkip failure can retry and then cache a valid negative result`() = runTest {
        val api = mockk<AniSkipApi>()
        var calls = 0
        coEvery { api.getSkipTimes(any(), any(), any(), any()) } coAnswers {
            calls++
            if (calls == 1) throw IOException("temporary provider outage")
            Response.success(AniSkipResponse(found = false))
        }
        val resolver = mockk<SimklIdResolver> {
            coEvery { resolveIds("mal", "123") } returns null
        }
        val repository = repository(aniSkipApi = api, simklResolver = resolver)

        assertTrue(repository.getSkipIntervalsForMal("123", 1).isEmpty())
        assertTrue(repository.getSkipIntervalsForMal("123", 1).isEmpty())
        assertTrue(repository.getSkipIntervalsForMal("123", 1).isEmpty())
        assertEquals("failure must be retried once; the later valid negative may be cached", 2, calls)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancellation does not cache an in-flight interval result`() = runTest {
        val api = mockk<AniSkipApi>()
        var calls = 0
        coEvery { api.getSkipTimes(any(), any(), any(), any()) } coAnswers {
            calls++
            if (calls == 1) awaitCancellation()
            Response.success(AniSkipResponse(found = false))
        }
        val resolver = mockk<SimklIdResolver> {
            coEvery { resolveIds("mal", "123") } returns null
        }
        val repository = repository(aniSkipApi = api, simklResolver = resolver)

        val job = launch { repository.getSkipIntervalsForMal("123", 1) }
        runCurrent()
        job.cancelAndJoin()

        assertTrue(repository.getSkipIntervalsForMal("123", 1).isEmpty())
        assertEquals(2, calls)
    }

    @Test
    fun `AnimeSkip show id lookup failure is retried before caching a negative`() = runTest {
        val animeSkipApi = mockk<AnimeSkipApi>()
        var showLookupCalls = 0
        coEvery { animeSkipApi.query(any(), any()) } coAnswers {
            val query = secondArg<com.nuvio.tv.data.remote.api.AnimeSkipRequest>().query
            if (query.contains("findShowsByExternalId")) {
                showLookupCalls++
                if (showLookupCalls == 1) throw IOException("temporary provider outage")
                Response.success(
                    AnimeSkipResponse(data = AnimeSkipData(findShowsByExternalId = emptyList())),
                )
            } else {
                Response.success(AnimeSkipResponse(data = AnimeSkipData(findEpisodesByShowId = emptyList())))
            }
        }
        val resolver = mockk<SimklIdResolver> {
            coEvery { resolveIdsForImdbEpisode("tt123", 1, 1) } returns
                SimklIdResolver.ResolvedIds(simklId = 7, type = "anime", anilist = "42")
            coEvery { getEpisodeMapping(7, "anime") } returns emptyList()
        }
        val settings = mockk<AnimeSkipSettingsDataStore> {
            every { clientId } returns flowOf("test-client")
            every { enabled } returns flowOf(true)
        }
        val introDbApi = mockk<IntroDbApi> {
            coEvery { getSegments(any(), any(), any(), any()) } returns
                Response.success(IntroDbSegmentsResponse())
        }
        val repository = repository(
            introDbApi = introDbApi,
            animeSkipApi = animeSkipApi,
            simklResolver = resolver,
            settings = settings,
        )

        assertTrue(repository.getSkipIntervals("tt123", 1, 1).isEmpty())
        assertTrue(repository.getSkipIntervals("tt123", 1, 1).isEmpty())
        assertTrue(repository.getSkipIntervals("tt123", 1, 1).isEmpty())
        assertEquals("failed show-id lookup must be retried, then negative-cached", 2, showLookupCalls)
    }

    private fun repository(
        introDbApi: IntroDbApi = mockk(relaxed = true),
        aniSkipApi: AniSkipApi = mockk(relaxed = true),
        animeSkipApi: AnimeSkipApi = mockk(relaxed = true),
        simklResolver: SimklIdResolver = mockk(relaxed = true),
        settings: AnimeSkipSettingsDataStore = mockk(relaxed = true),
    ) = run {
        DeviceCapabilities.clearCachedMemoryForTests()
        SkipIntroRepository(
            appContext = mockk<Context>(relaxed = true),
            introDbApi = introDbApi,
            aniSkipApi = aniSkipApi,
            animeSkipApi = animeSkipApi,
            simklResolver = simklResolver,
            animeSkipSettingsDataStore = settings,
            tmdbService = mockk<TmdbService>(relaxed = true),
        )
    }
}
