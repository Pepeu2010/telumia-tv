package com.nuvio.tv.data.trailer

import android.util.Log
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TmdbVideoResult
import com.nuvio.tv.data.remote.api.TmdbVideosResponse
import com.nuvio.tv.data.remote.api.TrailerApi
import com.nuvio.tv.domain.model.TmdbSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response

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
    fun `outer title cache expires when a stream URL expires`() = runTest {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val streamExpiry = clock.instant().plus(Duration.ofMinutes(5)).epochSecond
        val source = TrailerPlaybackSource(
            videoUrl = "https://example.test/expire/$streamExpiry/fake-video",
        )
        val harness = newHarness(clock, source)
        val renewedSource = TrailerPlaybackSource(
            videoUrl = "https://example.test/renewed-video",
        )
        coEvery { harness.extractor.extractPlaybackSource(any()) } returnsMany listOf(source, renewedSource)

        val first = harness.lookup()
        clock.advance(Duration.ofMinutes(6))
        val second = harness.lookup()

        assertNotNull(first)
        assertEquals(renewedSource, second)
        coVerify(exactly = 2) { harness.tmdbApi.getMovieVideos(any(), any(), any()) }
        coVerify(exactly = 2) { harness.extractor.extractPlaybackSource(any()) }
    }

    @Test
    fun `source without URL expiry is bounded by the explicit cache TTL`() = runTest {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val source = TrailerPlaybackSource(videoUrl = "https://example.test/fake-video")
        val harness = newHarness(clock, source)

        val first = harness.lookup()
        clock.advance(Duration.ofHours(3))
        val second = harness.lookup()

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(source, second)
        coVerify(exactly = 2) { harness.tmdbApi.getMovieVideos(any(), any(), any()) }
    }

    @Test
    fun `transient TMDB failure is not stored as a negative cache entry`() = runTest {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val source = TrailerPlaybackSource(videoUrl = "https://example.test/fake-video")
        val harness = newHarness(clock, source)
        val requests = AtomicInteger()
        coEvery { harness.tmdbApi.getMovieVideos(any(), any(), any()) } coAnswers {
            if (requests.incrementAndGet() == 1) {
                throw IOException("temporary TMDB failure")
            }
            movieVideosResponse()
        }

        assertNull(harness.lookup())
        val retry = harness.lookup()

        assertEquals(source, retry)
        assertEquals(2, requests.get())
        coVerify(exactly = 1) { harness.extractor.extractPlaybackSource(any()) }
    }

    private data class Harness(
        val service: TrailerService,
        val tmdbApi: TmdbApi,
        val extractor: InAppYouTubeExtractor,
    ) {
        suspend fun lookup(): TrailerPlaybackSource? = service.getTrailerPlaybackSource(
            title = "Fixture Movie",
            year = "2026",
            tmdbId = "123",
            type = "movie",
        )
    }

    private fun newHarness(clock: Clock, source: TrailerPlaybackSource): Harness {
        val tmdbApi = mockk<TmdbApi>(relaxed = true)
        val extractor = mockk<InAppYouTubeExtractor>()
        val settingsDataStore = mockk<TmdbSettingsDataStore> {
            every { settings } returns MutableStateFlow(
                TmdbSettings(language = "en", useTrailers = true),
            )
        }
        val tmdbService = mockk<TmdbService> {
            every { apiKey() } returns "tmdb-key"
        }
        coEvery { tmdbApi.getMovieVideos(any(), any(), any()) } returns movieVideosResponse()
        coEvery { extractor.extractPlaybackSource(any()) } returns source

        return Harness(
            service = TrailerService(
                trailerApi = mockk<TrailerApi>(relaxed = true),
                tmdbApi = tmdbApi,
                inAppYouTubeExtractor = extractor,
                tmdbSettingsDataStore = settingsDataStore,
                tmdbService = tmdbService,
                clock = clock,
            ),
            tmdbApi = tmdbApi,
            extractor = extractor,
        )
    }

    private fun movieVideosResponse() = Response.success(
        TmdbVideosResponse(
            id = 123,
            results = listOf(
                TmdbVideoResult(
                    iso6391 = "en",
                    name = "Fixture Trailer",
                    key = YOUTUBE_KEY,
                    site = "YouTube",
                    type = "Trailer",
                    official = true,
                ),
            ),
        ),
    )

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private companion object {
        const val YOUTUBE_KEY = "TESTtest123"
    }
}
