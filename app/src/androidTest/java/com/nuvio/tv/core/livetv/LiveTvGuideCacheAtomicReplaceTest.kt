package com.nuvio.tv.core.livetv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvImportResult
import com.nuvio.tv.domain.model.LiveTvProgram
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveTvGuideCacheAtomicReplaceTest {
    @Test
    fun replacesAnExistingGuideEntryOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "live-guide-atomic-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val cache = LiveTvGuideCache(directory, 1024L * 1024L, writesEnabled = true)
            val original = sampleResult("Jornal")
            val updated = sampleResult("Jornal atualizado")

            assertTrue(cache.write("source-a", original, updatedEpochMillis = 1L))
            assertTrue(cache.write("source-a", updated, updatedEpochMillis = 2L))

            assertEquals(updated, cache.read("source-a")?.result)
            assertEquals(2L, cache.read("source-a")?.updatedEpochMillis)
            assertEquals(1, directory.listFiles().orEmpty().count { it.name.endsWith(".bin.gz") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun deniedStoragePreflightKeepsTheExistingOfflineGuideOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "live-guide-preflight-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var canBeginWrite = true
            val cache = LiveTvGuideCache(
                directory,
                1024L * 1024L,
                writesEnabled = true,
                canBeginWrite = { canBeginWrite },
            )
            val original = sampleResult("Jornal")
            assertTrue(cache.write("source-a", original, updatedEpochMillis = 1L))
            val target = directory.listFiles().orEmpty().single { it.name.endsWith(".bin.gz") }
            val originalBytes = target.readBytes()

            canBeginWrite = false
            assertFalse(cache.write("source-a", sampleResult("Jornal atualizado"), updatedEpochMillis = 2L))

            assertTrue(target.readBytes().contentEquals(originalBytes))
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
            assertEquals(original, cache.read("source-a")?.result)
            assertEquals(1L, cache.read("source-a")?.updatedEpochMillis)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun sampleResult(title: String) = LiveTvImportResult(
        channels = listOf(LiveTvChannel("source-a:hash", "source-a", "Canal Brasil", "https://example.org/live.m3u8", tvgId = "br-1")),
        programs = listOf(LiveTvProgram("source-a", "br-1", title, 1_000L, 2_000L, description = "Notícias")),
    )
}
