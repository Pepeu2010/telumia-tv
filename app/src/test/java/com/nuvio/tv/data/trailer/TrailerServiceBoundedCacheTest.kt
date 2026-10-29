package com.nuvio.tv.data.trailer

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbVideosResponse
import com.nuvio.tv.data.remote.api.TrailerApi
import com.nuvio.tv.domain.model.TmdbSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Clock

class TrailerServiceBoundedCacheTest {
    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `youtube playback sources use access order before eviction`() = runTest {
        val trailerApi = mockk<TrailerApi>(relaxed = true)
        val tmdbApi = mockk<TmdbApi>(relaxed = true)
        val extractor = mockk<InAppYouTubeExtractor>()
        val settings = mockk<TmdbSettingsDataStore> {
            every { settings } returns MutableStateFlow(TmdbSettings(language = "en"))
        }
        val tmdbService = mockk<TmdbService> {
            every { apiKey() } returns "tmdb-key"
        }
        val service = TrailerService(
            trailerApi = trailerApi,
            tmdbApi = tmdbApi,
            inAppYouTubeExtractor = extractor,
            tmdbSettingsDataStore = settings,
            tmdbService = tmdbService,
            clock = Clock.systemUTC(),
            cacheLimits = TrailerCacheLimits(searchResults = 1, youtubePlaybackSources = 2),
        )
        val urlA = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val urlB = "https://www.youtube.com/watch?v=M7lc1UVf-VE"
        val urlC = "https://www.youtube.com/watch?v=9bZkp7q19f0"
        coEvery { extractor.extractPlaybackSource(urlA) } returns
            TrailerPlaybackSource(videoUrl = "https://cdn.example/a.mp4")
        coEvery { extractor.extractPlaybackSource(urlB) } returnsMany listOf(
            TrailerPlaybackSource(videoUrl = "https://cdn.example/b.mp4"),
            TrailerPlaybackSource(videoUrl = "https://cdn.example/b-reloaded.mp4"),
        )
        coEvery { extractor.extractPlaybackSource(urlC) } returns
            TrailerPlaybackSource(videoUrl = "https://cdn.example/c.mp4")

        service.getTrailerPlaybackSourceFromYouTubeUrl(urlA)
        service.getTrailerPlaybackSourceFromYouTubeUrl(urlB)
        service.getTrailerPlaybackSourceFromYouTubeUrl(urlA)
        service.getTrailerPlaybackSourceFromYouTubeUrl(urlC)
        val reloadedB = service.getTrailerPlaybackSourceFromYouTubeUrl(urlB)

        assertEquals("https://cdn.example/b-reloaded.mp4", reloadedB?.videoUrl)
        coVerify(exactly = 1) { extractor.extractPlaybackSource(urlA) }
        coVerify(exactly = 2) { extractor.extractPlaybackSource(urlB) }
        coVerify(exactly = 1) { extractor.extractPlaybackSource(urlC) }
    }

    @Test
    fun `negative search results are evicted at the configured capacity`() = runTest {
        val trailerApi = mockk<TrailerApi>(relaxed = true)
        val tmdbApi = mockk<TmdbApi>()
        val extractor = mockk<InAppYouTubeExtractor>(relaxed = true)
        val settings = mockk<TmdbSettingsDataStore> {
            every { settings } returns MutableStateFlow(
                TmdbSettings(language = "en", useTrailers = true)
            )
        }
        val tmdbService = mockk<TmdbService> {
            every { apiKey() } returns "tmdb-key"
        }
        coEvery { tmdbApi.getMovieVideos(any(), any(), any()) } returns
            Response.success(TmdbVideosResponse(id = 1, results = emptyList()))
        val service = TrailerService(
            trailerApi = trailerApi,
            tmdbApi = tmdbApi,
            inAppYouTubeExtractor = extractor,
            tmdbSettingsDataStore = settings,
            tmdbService = tmdbService,
            clock = Clock.systemUTC(),
            cacheLimits = TrailerCacheLimits(searchResults = 1, youtubePlaybackSources = 2),
        )

        assertNull(service.getTrailerPlaybackSource("First", tmdbId = "100", type = "movie"))
        assertNull(service.getTrailerPlaybackSource("Second", tmdbId = "200", type = "movie"))
        assertNull(service.getTrailerPlaybackSource("First", tmdbId = "100", type = "movie"))

        coVerify(exactly = 2) { tmdbApi.getMovieVideos(100, "tmdb-key", "en-US") }
        coVerify(exactly = 1) { tmdbApi.getMovieVideos(200, "tmdb-key", "en-US") }
    }
}
