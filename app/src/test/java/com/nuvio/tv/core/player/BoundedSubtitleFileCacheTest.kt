package com.nuvio.tv.core.player

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.Comparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedSubtitleFileCacheTest {
    @Test fun exactResponseLimitIsAcceptedAndOneByteOverIsRejectedAtTheFirstExtraByte() {
        val exactly = ByteArray(BoundedSubtitleFileCache.MAX_ENTRY_BYTES) { 1 }
        assertEquals(exactly.size, BoundedSubtitleFileCache.readAtMost(ByteArrayInputStream(exactly))!!.size)

        val oversized = CountingInputStream(ByteArray(BoundedSubtitleFileCache.MAX_ENTRY_BYTES + 100) { 2 })
        assertNull(BoundedSubtitleFileCache.readAtMost(oversized))
        assertEquals(BoundedSubtitleFileCache.MAX_ENTRY_BYTES + 1, oversized.bytesRead)
    }

    @Test fun aggregateQuotaRejectsWritesAndLeavesNoPartialFiles() = withCache { root ->
        var quota = 5L
        val cache = BoundedSubtitleFileCache(root, { quota }, { true }, maxEntryBytes = 4, maxFiles = 4)
        File(root, "subtitle-pending-crash.part").writeBytes(byteArrayOf(9))
        assertNotNull(cache.put("en_first.srt", byteArrayOf(1, 2, 3)))
        assertFalse(File(root, "subtitle-pending-crash.part").exists())
        assertNull(cache.put("en_second.srt", byteArrayOf(4, 5, 6)))
        assertEquals(3L, root.listFiles().orEmpty().sumOf { it.length() })

        quota = 8
        assertNotNull(cache.put("en_second.srt", byteArrayOf(4, 5, 6)))
        assertEquals(6L, root.listFiles().orEmpty().sumOf { it.length() })
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test fun entryCountAndFileNamesAreBounded() = withCache { root ->
        val cache = BoundedSubtitleFileCache(root, { 100 }, { true }, maxEntryBytes = 4, maxFiles = 1)
        assertNotNull(cache.put("mpv_1.vtt", byteArrayOf(1)))
        assertNull(cache.put("mpv_2.srt", byteArrayOf(2)))
        assertNull(cache.put("../outside.srt", byteArrayOf(3)))
        assertNull(cache.put("mpv_3.exe", byteArrayOf(4)))
        assertFalse(File(root.parentFile, "outside.srt").exists())
        assertEquals(1, root.listFiles().orEmpty().size)
    }

    private fun withCache(block: (File) -> Unit) {
        val root = Files.createTempDirectory("telumia-subtitle-cache-")
        try {
            block(root.toFile())
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    private class CountingInputStream(private val bytes: ByteArray) : InputStream() {
        var bytesRead = 0
            private set

        override fun read(): Int {
            if (bytesRead >= bytes.size) return -1
            return bytes[bytesRead++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= bytes.size) return -1
            val count = minOf(length, bytes.size - bytesRead)
            bytes.copyInto(buffer, offset, bytesRead, bytesRead + count)
            bytesRead += count
            return count
        }
    }
}
