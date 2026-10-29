package com.nuvio.tv.core.livetv

import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvImportResult
import com.nuvio.tv.domain.model.LiveTvProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class LiveTvGuideCacheTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun compressedGuideRoundTripsAndCanBeReadWhenWritesAreDisabled() = kotlinx.coroutines.runBlocking {
        val cacheDir = temporaryFolder.newFolder("epg")
        val cache = cache(cacheDir, 1024L * 1024L, writesEnabled = true)
        val expected = sampleResult()

        assertTrue(cache.write("source-a", expected, updatedEpochMillis = 1234L))
        val persistedBytes = cacheDir.listFiles()?.singleOrNull()?.length() ?: 0L
        assertTrue(persistedBytes in 1L..1024L * 1024L)

        val offlineReadOnlyCache = cache(cacheDir, 1024L * 1024L, writesEnabled = false)
        val actual = offlineReadOnlyCache.read("source-a")
        assertNotNull(actual)
        assertEquals(1234L, actual?.updatedEpochMillis)
        assertEquals(expected, actual?.result)
        assertFalse(offlineReadOnlyCache.write("source-b", expected))
    }

    @Test
    fun updatingSourceReplacesItsCacheEntryAtomically() = kotlinx.coroutines.runBlocking {
        val cacheDir = temporaryFolder.newFolder("replace-epg")
        val cache = cache(cacheDir, 1024L * 1024L, writesEnabled = true)
        val original = sampleResult()
        val updated = original.copy(programs = listOf(
            original.programs.single().copy(title = "Jornal atualizado")
        ))

        assertTrue(cache.write("source-a", original, updatedEpochMillis = 1L))
        assertTrue(cache.write("source-a", updated, updatedEpochMillis = 2L))

        assertEquals(updated, cache.read("source-a")?.result)
        assertEquals(2L, cache.read("source-a")?.updatedEpochMillis)
        assertEquals(1, cacheDir.listFiles()?.count { it.name.endsWith(".bin.gz") })
    }

    @Test
    fun storagePreflightDenialPreservesThePreviousOfflineGuide() = kotlinx.coroutines.runBlocking {
        val cacheDir = temporaryFolder.newFolder("epg-storage-preflight")
        val original = sampleResult()
        val writable = cache(cacheDir, 1024L * 1024L, writesEnabled = true)
        assertTrue(writable.write("source-a", original, updatedEpochMillis = 1L))
        val target = cacheDir.listFiles()?.single { it.name.endsWith(".bin.gz") } ?: error("cached guide missing")
        val originalBytes = target.readBytes()
        val updated = original.copy(programs = listOf(original.programs.single().copy(title = "Atualizado")))
        val storageConstrained = cache(
            cacheDir,
            1024L * 1024L,
            writesEnabled = true,
            canBeginWrite = { false },
        )

        assertFalse(storageConstrained.write("source-a", updated, updatedEpochMillis = 2L))
        assertTrue(target.readBytes().contentEquals(originalBytes))
        assertTrue(cacheDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        assertEquals(original, storageConstrained.read("source-a")?.result)
        assertEquals(1L, storageConstrained.read("source-a")?.updatedEpochMillis)
    }

    @Test
    fun refusesPayloadLargerThanTheEpgShareAndIgnoresUnknownSourceIds() = kotlinx.coroutines.runBlocking {
        val cacheDir = temporaryFolder.newFolder("small-epg")
        val cache = cache(cacheDir, quotaBytes = 1L, writesEnabled = true)

        assertFalse(cache.write("source-a", sampleResult()))
        assertNull(cache.read("source-a"))
        assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun evictsOlderSourceCacheToStayWithinTheEpgShare() = kotlinx.coroutines.runBlocking {
        val cacheDir = temporaryFolder.newFolder("bounded-epg")
        val first = cache(cacheDir, 1024L * 1024L, writesEnabled = true)
        assertTrue(first.write("source-a", sampleResult(), updatedEpochMillis = 1L))
        val firstEntryBytes = cacheDir.listFiles()?.singleOrNull()?.length() ?: error("cache file missing")
        val sizingDir = temporaryFolder.newFolder("sizing-epg")
        val sizingCache = cache(sizingDir, 1024L * 1024L, writesEnabled = true)
        assertTrue(sizingCache.write("source-b", sampleResult(), updatedEpochMillis = 2L))
        val secondEntryBytes = sizingDir.listFiles()?.singleOrNull()?.length() ?: error("sizing cache file missing")
        val quota = maxOf(firstEntryBytes, secondEntryBytes)

        val singleEntryBudget = cache(cacheDir, quota, writesEnabled = true)
        assertTrue(singleEntryBudget.write("source-b", sampleResult(), updatedEpochMillis = 2L))

        assertNull(singleEntryBudget.read("source-a"))
        assertNotNull(singleEntryBudget.read("source-b"))
        assertTrue(cacheDir.listFiles().orEmpty().filter { it.name.endsWith(".bin.gz") }.sumOf { it.length() } <= quota)
    }

    private fun sampleResult() = LiveTvImportResult(
        channels = listOf(LiveTvChannel("source-a:hash", "source-a", "Canal Brasil", "https://example.org/live.m3u8", tvgId = "br-1")),
        programs = listOf(LiveTvProgram("source-a", "br-1", "Jornal", 1000L, 2000L, description = "Notícias")),
    )

    /** JVM tests run on Windows too; use the host's atomic replace equivalent to Android rename(2). */
    private fun cache(
        directory: java.io.File,
        quotaBytes: Long,
        writesEnabled: Boolean,
        canBeginWrite: () -> Boolean = { true },
    ) = LiveTvGuideCache(directory, quotaBytes, writesEnabled,
        atomicReplace = { source, target ->
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            true
        },
        canBeginWrite = canBeginWrite,
    )
}
