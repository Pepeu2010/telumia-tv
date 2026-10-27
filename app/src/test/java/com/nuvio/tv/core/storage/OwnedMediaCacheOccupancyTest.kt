package com.nuvio.tv.core.storage

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import org.junit.Test
import org.junit.Assert.*

class OwnedMediaCacheOccupancyTest {
    private fun fixture(test: (Path) -> Unit) {
        val root = Files.createTempDirectory("telumia-cache-occupancy-")
        try { test(root) } finally {
            Files.walk(root).use { files -> files.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
    private fun write(root: Path, name: String, size: Int) {
        val file = root.resolve(name)
        Files.createDirectories(file.parent)
        Files.write(file, ByteArray(size))
    }
    @Test fun measuresOnlyAllowlistedCachesAndKeepsDurableFilesIntact() = fixture { root ->
        write(root, "media-cache-v1/images/coil/first", 7)
        write(root, "media-cache-v1/images/gifs/second", 11)
        write(root, "gif-cache/legacy.gif", 13)
        write(root, "media-cache-v1/unrecognized/future", 23)
        write(root, "profile-studio/avatar.png", 29)
        write(root, "downloads/movie", 31)
        val result = OwnedMediaCacheOccupancy.measure(root,
            listOf(listOf("media-cache-v1", "images"), listOf("gif-cache")), maxMillis = 10_000)
        assertTrue(result.complete)
        assertEquals(31L, result.bytes)
        assertEquals(29L, Files.size(root.resolve("profile-studio/avatar.png")))
        assertEquals(31L, Files.size(root.resolve("downloads/movie")))
    }

    @Test fun countsCurrentAndLegacyHttpCacheFilesButIgnoresOtherData() = fixture { root ->
        write(root, "http_cache_v2/journal", 7)
        write(root, "addon_http_cache/journal", 11)
        write(root, "http_cache/legacy-journal", 5)
        write(root, "metadata/ratings.json", 13)
        write(root, "downloads/movie", 31)
        val result = OwnedMediaCacheOccupancy.measure(
            root,
            listOf(listOf("http_cache_v2"), listOf("addon_http_cache"), listOf("http_cache"), listOf("metadata")),
            maxMillis = 10_000,
        )
        assertTrue(result.complete)
        assertEquals(36L, result.bytes)
        assertEquals(5L, Files.size(root.resolve("http_cache/legacy-journal")))
        assertEquals(31L, Files.size(root.resolve("downloads/movie")))
    }

    @Test fun previewImageCacheIsMeasuredFromItsDedicatedCategoryDirectory() = fixture { root ->
        write(root, "media-cache-v1/previews/coil/preview.webp", 47)
        write(root, "media-cache-v1/images/coil/poster.webp", 31)
        val result = OwnedMediaCacheOccupancy.measure(
            root,
            listOf(listOf("media-cache-v1", "previews")),
            maxMillis = 10_000,
        )
        assertTrue(result.complete)
        assertEquals(47L, result.bytes)
        assertEquals(31L, Files.size(root.resolve("media-cache-v1/images/coil/poster.webp")))
    }

    @Test fun legacyMdbListCacheInFilesDirectoryCountsWithoutIncludingDurableData() = fixture { root ->
        val cacheDirectory = root.resolve("cache").also(Files::createDirectories)
        val filesDirectory = root.resolve("files").also(Files::createDirectories)
        write(filesDirectory, "mdblist_cache/ratings_v1.json", 37)
        write(filesDirectory, "cw_enrichment/nextup_1.json", 17)
        write(filesDirectory, "datastore/account.preferences_pb", 29)
        write(filesDirectory, "scene-bookmarks-v1/bookmarks.json", 11)
        val preferences = mockk<android.content.SharedPreferences>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.cacheDir } returns cacheDirectory.toFile()
        every { context.filesDir } returns filesDirectory.toFile()
        every { context.getSharedPreferences("telumia_media_cache_v1", Context.MODE_PRIVATE) } returns preferences

        val result = TvMediaCache(context).occupiedCache

        assertTrue(result.complete)
        assertEquals(54L, result.bytes)
        assertEquals(29L, Files.size(filesDirectory.resolve("datastore/account.preferences_pb")))
        assertEquals(11L, Files.size(filesDirectory.resolve("scene-bookmarks-v1/bookmarks.json")))
    }
    @Test fun memberAvatarDiskCacheIsCountedWithoutIncludingDurableProfileAvatarData() = fixture { root ->
        write(root, "member_profile_avatars/member-one-v3.webp", 17)
        write(root, "member_profile_backgrounds/member-one-v3.png", 19)
        write(root, "member_profile_backgrounds_v2/member-one-v3.png", 23)
        write(root, "profile-studio-v1/avatars/local-avatar.png", 29)
        val result = OwnedMediaCacheOccupancy.measure(
            root,
            listOf(
                listOf("member_profile_avatars"),
                listOf("member_profile_backgrounds"),
                listOf("member_profile_backgrounds_v2"),
            ),
            maxMillis = 10_000,
        )
        assertTrue(result.complete)
        assertEquals(59L, result.bytes)
        assertEquals(29L, Files.size(root.resolve("profile-studio-v1/avatars/local-avatar.png")))
    }

    @Test fun missingCachesAreAnEmptyMeasurement() = fixture { root ->
        assertEquals(MediaCacheOccupancy(0, 0, true),
            OwnedMediaCacheOccupancy.measure(root, listOf(listOf("missing")), maxMillis = 10_000))
    }
    @Test fun entryLimitReturnsAnExplicitConservativePartialMeasurement() = fixture { root ->
        repeat(30) { write(root, "images/$it", 4) }
        val result = OwnedMediaCacheOccupancy.measure(root, listOf(listOf("images")), maxEntries = 3, maxMillis = 10_000)
        assertFalse(result.complete)
        assertEquals(3, result.visitedEntries)
        assertEquals(8L, result.bytes)
    }
    @Test fun depthLimitDoesNotEnterUnboundedSubtrees() = fixture { root ->
        write(root, "images/deep/inner/third", 37)
        val result = OwnedMediaCacheOccupancy.measure(root, listOf(listOf("images")), maxDepth = 1, maxMillis = 10_000)
        assertFalse(result.complete)
        assertEquals(0L, result.bytes)
        assertEquals(37L, Files.size(root.resolve("images/deep/inner/third")))
    }
    @Test fun duplicateRootsDoNotDoubleCountFiles() = fixture { root ->
        write(root, "images/one", 7)
        val result = OwnedMediaCacheOccupancy.measure(root,
            listOf(listOf("images"), listOf("images")), maxMillis = 10_000)
        assertTrue(result.complete)
        assertEquals(7L, result.bytes)
    }
    @Test fun rejectsDirectoryTraversalBeforeReading() = fixture { root ->
        for (part in listOf("..", ".", "images/other", "images\\other")) {
            try {
                OwnedMediaCacheOccupancy.measure(root, listOf(listOf(part)))
                fail("Unsafe cache root was accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun junctionOrSymlinkOutsideTheCacheIsNotCounted() = fixture { root ->
        val outside = Files.createTempDirectory("telumia-occupancy-outside-")
        try {
            write(outside, "private-file", 101)
            Files.createDirectories(root.resolve("images"))
            val link = root.resolve("images/linked")
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                val process = ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), outside.toString())
                    .redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                assertEquals(output, 0, process.waitFor())
            } else Files.createSymbolicLink(link, outside)
            try {
                val result = OwnedMediaCacheOccupancy.measure(root, listOf(listOf("images")), maxMillis = 10_000)
                assertTrue(result.complete)
                assertEquals(0L, result.bytes)
                assertEquals(101L, Files.size(outside.resolve("private-file")))
            } finally { Files.delete(link) }
        } finally { Files.deleteIfExists(outside.resolve("private-file")); Files.delete(outside) }
    }
}
