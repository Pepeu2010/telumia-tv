package com.nuvio.tv.core.storage

import java.io.File
import java.nio.file.Files
import java.util.Comparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedMemberImageCacheTest {
    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("telumia-avatar-cache-")
        try {
            block(root.toFile())
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    @Test fun cachedAvatarRemainsReadableAfterWritesAreDisabled() = fixture { root ->
        var writable = true
        val cache = BoundedMemberImageCache(root, { 32 }, { writable }, clockMillis = { 10L })
        val stored = cache.put("member_avatar", 3, "png", byteArrayOf(1, 2, 3))
        assertNotNull(stored)
        writable = false
        assertEquals(stored, cache.get("member_avatar", 3, "png"))
        assertNull(cache.put("another_avatar", 1, "png", byteArrayOf(4)))
    }

    @Test fun quotaShrinkEvictsLeastRecentlyUsedOwnedEntriesOnly() = fixture { root ->
        var now = 1_000L
        var quota = 9L
        val cache = BoundedMemberImageCache(root, { quota }, { true }, clockMillis = { now })
        val payload = byteArrayOf(1, 2, 3)
        assertNotNull(cache.put("avatar_a", 1, "webp", payload))
        now += 1_000L
        assertNotNull(cache.put("avatar_b", 1, "webp", payload))
        now += 1_000L
        assertNotNull(cache.get("avatar_a", 1, "webp"))
        now += 1_000L
        quota = 6
        assertNotNull(cache.put("avatar_c", 1, "webp", payload))
        assertNotNull(cache.get("avatar_a", 1, "webp"))
        assertNull(cache.get("avatar_b", 1, "webp"))
        assertNotNull(cache.get("avatar_c", 1, "webp"))
        assertEquals(6L, root.listFiles()!!.sumOf { it.length() })
    }

    @Test fun oversizedEntriesAndUnsafeNamesAreRejected() = fixture { root ->
        val cache = BoundedMemberImageCache(root, { 32 }, { true }, maxEntryBytes = 4)
        assertNull(cache.put("avatar", 1, "png", ByteArray(5)))
        assertNull(cache.put("../escape", 1, "png", byteArrayOf(1)))
        assertNull(cache.put("avatar", -1, "png", byteArrayOf(1)))
        assertNull(cache.put("avatar", 1, "../../private", byteArrayOf(1)))
        assertFalse(File(root.parentFile, "escape-v1.png").exists())
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun latestCachedVersionIsSelectedAndTouchedForLru() = fixture { root ->
        var now = 1_000L
        val cache = BoundedMemberImageCache(root, { 32 }, { true }, clockMillis = { now })
        cache.put("avatar-vip", 1, "png", byteArrayOf(1))
        now = 2_000L
        val latest = cache.put("avatar-vip", 2, "jpg", byteArrayOf(2))
        assertEquals(latest, cache.latest("avatar-vip"))
        assertTrue(latest!!.lastModified() >= 2_000L)
    }

    @Test fun memberBackgroundEntryCanUseItsConfiguredLargerPerFileLimit() = fixture { root ->
        val cache = BoundedMemberImageCache(root, { 8 }, { true }, maxEntryBytes = 8, maxFiles = 4)
        assertNotNull(cache.put("background", 1, "png", ByteArray(8)))
        assertNull(cache.put("background", 2, "png", ByteArray(9)))
    }
}
