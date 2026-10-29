package com.nuvio.tv.data.mdblist

import android.content.Context
import com.nuvio.tv.domain.model.MDBListRatings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MdbListRatingsDiskCacheTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun writesStayWithinMetadataQuotaAndEvictOldestEntriesFirst() = runTest {
        val context = context()
        val cache = MdbListRatingsDiskCache(context, scope = backgroundScope, now = { currentTime },
            quotaBytes = { 500L }, writesEnabled = { true }, canWrite = { true }, ioDispatcher = StandardTestDispatcher(testScheduler))
        val ratings = MDBListRatings(imdb = 8.1, tomatoes = 93.0, tomatoesCertified = true)
        currentTime = 100L
        repeat(10) { index ->
            currentTime += 1
            cache.put("imdb:movie:tt$index", ratings.copy(imdb = index.toDouble()))
        }

        advanceTimeBy(2_000L)
        runCurrent()

        val saved = File(context.cacheDir, "metadata/mdblist/ratings_v1.json")
        assertTrue(saved.isFile)
        assertTrue("${saved.length()} bytes exceeded a 500 byte metadata share", saved.length() <= 500L)

        val restored = MdbListRatingsDiskCache(context, scope = backgroundScope, now = { currentTime },
            quotaBytes = { 500L }, writesEnabled = { true }, canWrite = { true }, ioDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(9.0, requireNotNull(restored.get("imdb:movie:tt9")).imdb!!, 0.0)
        assertNull(restored.get("imdb:movie:tt0"))
    }

    @Test
    fun legacyDurableCacheIsReadThenRemovedFromFilesDir() = runTest {
        val context = context()
        val legacy = File(context.filesDir, "mdblist_cache/ratings_v1.json").apply {
            parentFile?.mkdirs()
            writeText(JSONObject().put("imdb:movie:tt123", JSONObject().apply {
                put("t", currentTime)
                put("imdb", 7.7)
                put("tc", true)
            }).toString())
        }

        val cache = MdbListRatingsDiskCache(context, scope = backgroundScope, now = { currentTime },
            quotaBytes = { 500L }, writesEnabled = { true }, canWrite = { true }, ioDispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(MDBListRatings(imdb = 7.7, tomatoesCertified = true), cache.get("imdb:movie:tt123"))
        assertTrue("Legacy data remains until the bounded copy is committed", legacy.exists())

        advanceTimeBy(2_000L)
        runCurrent()

        assertTrue(File(context.cacheDir, "metadata/mdblist/ratings_v1.json").isFile)
        assertFalse("The old cache must no longer occupy durable app data", legacy.exists())
    }

    @Test
    fun readOnlyModeKeepsLegacyMetadataAvailableAcrossRestart() = runTest {
        val context = context()
        val legacy = File(context.filesDir, "mdblist_cache/ratings_v1.json").apply {
            parentFile?.mkdirs()
            writeText(JSONObject().put("imdb:movie:tt456", JSONObject().apply {
                put("t", currentTime)
                put("imdb", 6.9)
            }).toString())
        }
        val readOnly = MdbListRatingsDiskCache(
            context,
            scope = backgroundScope,
            now = { currentTime },
            quotaBytes = { 500L },
            writesEnabled = { false },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(6.9, requireNotNull(readOnly.get("imdb:movie:tt456")).imdb!!, 0.0)
        assertTrue("Read-only mode must not delete the only persisted copy", legacy.exists())
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(legacy.exists())

        val afterRestart = MdbListRatingsDiskCache(
            context,
            scope = backgroundScope,
            now = { currentTime },
            quotaBytes = { 500L },
            writesEnabled = { false },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        assertEquals(6.9, requireNotNull(afterRestart.get("imdb:movie:tt456")).imdb!!, 0.0)
    }

    @Test
    fun malformedBoundedCopyFallsBackToIntactLegacyMetadata() = runTest {
        val context = context()
        val legacy = File(context.filesDir, "mdblist_cache/ratings_v1.json").apply {
            parentFile?.mkdirs()
            writeText(JSONObject().put("imdb:movie:tt789", JSONObject().apply {
                put("t", currentTime)
                put("imdb", 8.2)
            }).toString())
        }
        val current = File(context.cacheDir, "metadata/mdblist/ratings_v1.json").apply {
            parentFile?.mkdirs()
            writeText("partial")
        }
        val readOnly = MdbListRatingsDiskCache(
            context,
            scope = backgroundScope,
            now = { currentTime },
            quotaBytes = { 500L },
            writesEnabled = { false },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(8.2, requireNotNull(readOnly.get("imdb:movie:tt789")).imdb!!, 0.0)
        assertTrue("The invalid partial file is left untouched in read-only mode", current.exists())
        assertTrue(legacy.exists())
    }

    @Test
    fun legacyMetadataRemainsWhenQuotaCannotFitTheMigratedEntry() = runTest {
        val context = context()
        val legacy = File(context.filesDir, "mdblist_cache/ratings_v1.json").apply {
            parentFile?.mkdirs()
            writeText(JSONObject().put("imdb:movie:tt987", JSONObject().apply {
                put("t", currentTime)
                put("imdb", 7.8)
            }).toString())
        }
        val cache = MdbListRatingsDiskCache(
            context,
            scope = backgroundScope,
            now = { currentTime },
            quotaBytes = { 1L },
            writesEnabled = { true },
            canWrite = { true },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(7.8, requireNotNull(cache.get("imdb:movie:tt987")).imdb!!, 0.0)
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue("The only copy must survive a quota too small to migrate it", legacy.exists())
    }

    @Test
    fun readOnlyModeKeepsExistingMetadataReadableAndRejectsWrites() = runTest {
        val context = context()
        val writable = MdbListRatingsDiskCache(context, scope = backgroundScope, now = { currentTime },
            quotaBytes = { 500L }, writesEnabled = { true }, canWrite = { true }, ioDispatcher = StandardTestDispatcher(testScheduler))
        writable.put("imdb:movie:tt1", MDBListRatings(imdb = 8.4))
        advanceTimeBy(2_000L)
        runCurrent()
        val saved = File(context.cacheDir, "metadata/mdblist/ratings_v1.json")
        val originalBytes = saved.readBytes()

        val readOnly = MdbListRatingsDiskCache(
            context,
            scope = backgroundScope,
            now = { currentTime },
            quotaBytes = { 500L },
            writesEnabled = { false },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        assertNotNull(readOnly.get("imdb:movie:tt1"))
        readOnly.put("imdb:movie:tt2", MDBListRatings(imdb = 7.1))
        advanceTimeBy(2_000L)
        runCurrent()

        assertTrue(originalBytes.contentEquals(saved.readBytes()))
        val afterRestart = MdbListRatingsDiskCache(
            context,
            scope = backgroundScope,
            now = { currentTime },
            quotaBytes = { 500L },
            writesEnabled = { false },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        assertNull(afterRestart.get("imdb:movie:tt2"))
    }

    @Test
    fun storageReservePreservesReadableMetadataAndSkipsTheReplacementWrite() = runTest {
        val context = context()
        val writable = MdbListRatingsDiskCache(
            context,
            scope = backgroundScope,
            now = { currentTime },
            quotaBytes = { 500L },
            writesEnabled = { true },
            canWrite = { true },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        writable.put("imdb:movie:tt111", MDBListRatings(imdb = 8.0))
        advanceTimeBy(2_000L)
        runCurrent()
        val saved = File(context.cacheDir, "metadata/mdblist/ratings_v1.json")
        val originalBytes = saved.readBytes()

        var attemptedBytes = 0L
        val storageBlocked = MdbListRatingsDiskCache(
            context,
            scope = backgroundScope,
            now = { currentTime },
            quotaBytes = { 500L },
            writesEnabled = { true },
            canWrite = { bytes -> attemptedBytes = bytes; false },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        assertEquals(8.0, requireNotNull(storageBlocked.get("imdb:movie:tt111")).imdb!!, 0.0)
        storageBlocked.put("imdb:movie:tt222", MDBListRatings(imdb = 7.2))
        advanceTimeBy(2_000L)
        runCurrent()

        assertTrue("The reserve gate is checked with the encoded snapshot size", attemptedBytes > 0L)
        assertTrue("A blocked write leaves the last valid cache file intact", originalBytes.contentEquals(saved.readBytes()))
        assertFalse(File(saved.parentFile, "${saved.name}.tmp").exists())

        val afterRestart = MdbListRatingsDiskCache(
            context,
            scope = backgroundScope,
            now = { currentTime },
            quotaBytes = { 500L },
            writesEnabled = { true },
            canWrite = { false },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        assertNotNull(afterRestart.get("imdb:movie:tt111"))
        assertNull(afterRestart.get("imdb:movie:tt222"))
    }

    @Test
    fun rejectsCacheFileLargerThanTheBoundedReadLimit() = runTest {
        val context = context()
        val file = File(context.cacheDir, "metadata/mdblist/ratings_v1.json").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(MdbListRatingsDiskCache.MAX_CACHE_FILE_BYTES.toInt() + 1))
        }
        assertTrue(file.exists())

        val cache = MdbListRatingsDiskCache(context, scope = backgroundScope, now = { currentTime },
            quotaBytes = { 500L }, writesEnabled = { true }, canWrite = { true }, ioDispatcher = StandardTestDispatcher(testScheduler))
        assertNull(cache.get("imdb:movie:tt1"))
    }

    @Test
    fun expiredRatingsAreRemovedFromTheDiskCache() = runTest {
        val context = context()
        val cache = MdbListRatingsDiskCache(context, scope = backgroundScope, now = { currentTime },
            quotaBytes = { 500L }, writesEnabled = { true }, canWrite = { true }, ioDispatcher = StandardTestDispatcher(testScheduler))
        cache.put("imdb:movie:tt1", MDBListRatings(imdb = 8.1))
        advanceTimeBy(2_000L)
        runCurrent()
        val saved = File(context.cacheDir, "metadata/mdblist/ratings_v1.json")
        assertTrue(saved.isFile)

        currentTime += 13L * 60L * 60L * 1_000L
        assertNull(cache.get("imdb:movie:tt1"))
        advanceTimeBy(2_000L)
        runCurrent()

        assertFalse("Expired ratings must be evicted from disk", saved.exists())
    }

    private var currentTime = 1_000_000L

    private fun context(): Context {
        val root = temporaryFolder.newFolder("cache-${System.nanoTime()}")
        val cacheRoot = File(root, "cache").apply { mkdirs() }
        val filesRoot = File(root, "files").apply { mkdirs() }
        return mockk(relaxed = true) {
            every { cacheDir } returns cacheRoot
            every { filesDir } returns filesRoot
        }
    }
}
