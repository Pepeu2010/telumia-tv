package com.nuvio.tv.data.repository

import com.nuvio.tv.data.remote.api.ImdbApiParentsGuideCategory
import com.nuvio.tv.data.remote.api.ImdbApiParentsGuideResponse
import com.nuvio.tv.data.remote.api.ImdbApiSeverityBreakdown
import com.nuvio.tv.data.remote.api.ParentalGuideApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ParentalGuideRepositoryCacheTest {
    @Test
    fun `successful guide is cached by IMDb id`() = runTest {
        val api = mockk<ParentalGuideApi>()
        coEvery { api.getParentsGuide("tt123") } returns Response.success(guide())
        val repository = ParentalGuideRepository(api)

        val first = repository.getParentalGuide("tt123")
        val second = repository.getParentalGuide("tt123")

        val expected = ParentalGuideResult(nudity = "moderate")
        assertEquals(expected, first)
        assertEquals(expected, second)
        coVerify(exactly = 1) { api.getParentsGuide("tt123") }
    }

    @Test
    fun `empty guide response remains a miss and later response can recover`() = runTest {
        val api = mockk<ParentalGuideApi>()
        var calls = 0
        coEvery { api.getParentsGuide("tt123") } coAnswers {
            calls++
            if (calls == 1) {
                Response.success(ImdbApiParentsGuideResponse(parentsGuide = emptyList()))
            } else {
                Response.success(guide())
            }
        }
        val repository = ParentalGuideRepository(api)

        assertNull(repository.getParentalGuide("tt123"))
        assertEquals(ParentalGuideResult(nudity = "moderate"), repository.getParentalGuide("tt123"))
        assertEquals(ParentalGuideResult(nudity = "moderate"), repository.getParentalGuide("tt123"))
        assertEquals("empty results should stay retryable while successful data is cached", 2, calls)
    }

    @Test
    fun `non IMDb ids do not reach the API`() = runTest {
        val api = mockk<ParentalGuideApi>()
        val repository = ParentalGuideRepository(api)

        assertNull(repository.getParentalGuide("tmdb:42"))

        coVerify(exactly = 0) { api.getParentsGuide(any()) }
    }

    @Test
    fun `cache expires entries and evicts least recently used`() {
        var now = 1_000L
        val cache = ParentalGuideCache(
            limits = ParentalGuideCacheLimits(entries = 2, ttlMillis = 100L),
            nowMillis = { now },
        )

        cache.put("tt1", ParentalGuideResult(nudity = "mild"))
        cache.put("tt2", ParentalGuideResult(violence = "moderate"))
        assertEquals(ParentalGuideResult(nudity = "mild"), cache.get("tt1"))
        cache.put("tt3", ParentalGuideResult(profanity = "severe"))

        assertNull("least recently used result should be evicted", cache.get("tt2"))
        assertEquals(ParentalGuideResult(nudity = "mild"), cache.get("tt1"))
        assertEquals(ParentalGuideResult(profanity = "severe"), cache.get("tt3"))
        assertEquals(2, cache.size)

        now = 1_101L
        assertNull("entries older than the configured TTL should expire", cache.get("tt1"))
        assertNull(cache.get("tt3"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `concurrent cache access keeps values isolated and within capacity`() {
        val cache = ParentalGuideCache(
            limits = ParentalGuideCacheLimits(entries = 8, ttlMillis = 60_000L),
            nowMillis = { 1_000L },
        )
        val workers = 6
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val threads = (0 until workers).map { worker ->
            Thread {
                ready.countDown()
                if (!start.await(5, TimeUnit.SECONDS)) {
                    failure.compareAndSet(null, AssertionError("concurrent workers did not start"))
                    return@Thread
                }
                repeat(500) { item ->
                    val key = "tt$worker-$item"
                    val value = ParentalGuideResult(nudity = "$worker:$item")
                    cache.put(key, value)
                    val read = cache.get(key)
                    if (read != null && read != value) {
                        failure.compareAndSet(null, AssertionError("cache value crossed keys for $key"))
                    }
                }
            }.apply(Thread::start)
        }

        assertTrue("workers should be ready", ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        threads.forEach { thread ->
            thread.join(5_000L)
            assertFalse("cache worker should finish", thread.isAlive)
        }

        failure.get()?.let { throw AssertionError("concurrent parental guide cache access failed", it) }
        assertEquals(8, cache.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `API cancellation propagates to the playback caller`() = runTest {
        val api = mockk<ParentalGuideApi>()
        coEvery { api.getParentsGuide(any()) } coAnswers { awaitCancellation() }
        val repository = ParentalGuideRepository(api)
        var returnedAfterCancellation = false

        val job = launch {
            repository.getParentalGuide("tt123")
            returnedAfterCancellation = true
        }
        runCurrent()
        job.cancelAndJoin()

        assertFalse("cancelled playback request must not be turned into an empty success", returnedAfterCancellation)
    }

    private fun guide() = ImdbApiParentsGuideResponse(
        parentsGuide = listOf(
            ImdbApiParentsGuideCategory(
                category = "SEXUAL_CONTENT",
                severityBreakdowns = listOf(
                    ImdbApiSeverityBreakdown(severityLevel = "moderate", voteCount = 4),
                    ImdbApiSeverityBreakdown(severityLevel = "mild", voteCount = 2),
                    ImdbApiSeverityBreakdown(severityLevel = "none", voteCount = 1),
                ),
            ),
        ),
    )
}
