package com.nuvio.tv.core.storage

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

    @Test fun directCacheRootFilesCountWithoutWalkingChildDirectories() = fixture { root ->
        write(root, "mpv-shader-cache-entry", 17)
        write(root, "stale-frame-probe.tmp", 19)
        write(root, "image_cache/poster.webp", 23)
        write(root, "downloads/movie.mkv", 29)
        val originalBytes = Files.readAllBytes(root.resolve("mpv-shader-cache-entry"))

        val result = DirectCacheFileOccupancy.measure(root, maxMillis = 10_000)

        assertTrue(result.complete)
        assertEquals(36L, result.bytes)
        assertArrayEquals(originalBytes, Files.readAllBytes(root.resolve("mpv-shader-cache-entry")))
        assertEquals(23L, Files.size(root.resolve("image_cache/poster.webp")))
        assertEquals(29L, Files.size(root.resolve("downloads/movie.mkv")))
    }

    @Test fun directCacheRootFileScanReportsEntryLimit() = fixture { root ->
        repeat(4) { write(root, "root-file-$it.tmp", 5) }

        val result = DirectCacheFileOccupancy.measure(root, maxEntries = 2, maxMillis = 10_000)

        assertFalse(result.complete)
        assertEquals(2, result.visitedEntries)
        assertTrue(result.bytes <= 10L)
    }

    @Test fun autoOccupancyAddsDirectCacheFilesWithoutDoubleCountingAllowlistedDirectories() = fixture { root ->
        val cacheDirectory = root.resolve("cache").also(Files::createDirectories)
        val filesDirectory = root.resolve("files").also(Files::createDirectories)
        write(cacheDirectory, "mpv-shader-cache-entry", 31)
        write(cacheDirectory, "image_cache/poster.webp", 37)
        write(cacheDirectory, "downloads/movie.mkv", 41)

        val result = measureTvMediaCacheOccupancy(cacheDirectory, filesDirectory)

        assertTrue(result.complete)
        assertEquals(68L, result.bytes)
        assertEquals(41L, Files.size(cacheDirectory.resolve("downloads/movie.mkv")))
    }

    @Test fun profileScopedStreamAndBingeDataStoreFilesAndBackupsCountTowardAutoOccupancy() = fixture { root ->
        val cacheDirectory = root.resolve("cache").also(Files::createDirectories)
        val filesDirectory = root.resolve("files").also(Files::createDirectories)
        write(filesDirectory, "datastore/stream_link_cache.preferences_pb", 11)
        write(filesDirectory, "datastore/stream_link_cache.preferences_pb.bak", 13)
        write(filesDirectory, "datastore/stream_link_cache_p6.preferences_pb", 17)
        write(filesDirectory, "datastore/stream_link_cache_p6.preferences_pb.bak", 19)
        write(filesDirectory, "datastore/binge_group_cache.preferences_pb", 23)
        write(filesDirectory, "datastore/binge_group_cache.preferences_pb.bak", 29)
        write(filesDirectory, "datastore/unrelated_durable_preferences.preferences_pb", 31)

        val result = measureTvMediaCacheOccupancy(cacheDirectory, filesDirectory)

        assertTrue(result.complete)
        assertEquals(112L, result.bytes)
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
        val result = measureTvMediaCacheOccupancy(cacheDirectory, filesDirectory)

        assertTrue(result.complete)
        assertEquals(54L, result.bytes)
        assertEquals(29L, Files.size(filesDirectory.resolve("datastore/account.preferences_pb")))
        assertEquals(11L, Files.size(filesDirectory.resolve("scene-bookmarks-v1/bookmarks.json")))
    }

    @Test fun profilePluginCodeCachesCountWithoutIncludingDurableFilesOrUnrecognizedFolders() = fixture { root ->
        val cacheDirectory = root.resolve("cache").also(Files::createDirectories)
        val filesDirectory = root.resolve("files").also(Files::createDirectories)
        write(filesDirectory, "plugin_code/scraper.js", 7)
        write(filesDirectory, "plugin_code_p2/scraper.js", 11)
        write(filesDirectory, "plugin_code_p17/other.js", 13)
        write(filesDirectory, "plugin_code_p2_backup/should-not-count.js", 17)
        write(filesDirectory, "plugin_code_p/not-a-profile.js", 19)
        write(filesDirectory, "datastore/account.preferences_pb", 23)
        write(filesDirectory, "profile-studio-v1/avatars/local.png", 29)

        val result = measureTvMediaCacheOccupancy(cacheDirectory, filesDirectory)

        assertTrue(result.complete)
        assertEquals(31L, result.bytes)
        assertEquals(17L, Files.size(filesDirectory.resolve("plugin_code_p2_backup/should-not-count.js")))
        assertEquals(23L, Files.size(filesDirectory.resolve("datastore/account.preferences_pb")))
        assertEquals(29L, Files.size(filesDirectory.resolve("profile-studio-v1/avatars/local.png")))
    }

    @Test fun stagedUpdaterApkCountsWithoutChangingItsContentsOrIncludingOtherFiles() = fixture { root ->
        val cacheDirectory = root.resolve("cache").also(Files::createDirectories)
        val filesDirectory = root.resolve("files").also(Files::createDirectories)
        val stagedApk = cacheDirectory.resolve("updates/telumia-update.apk")
        Files.createDirectories(stagedApk.parent)
        val originalBytes = ByteArray(41) { it.toByte() }
        Files.write(stagedApk, originalBytes)
        write(cacheDirectory, "updates-backup/old.apk", 43)
        write(cacheDirectory, "downloads/movie.mkv", 47)

        val result = measureTvMediaCacheOccupancy(cacheDirectory, filesDirectory)

        assertTrue(result.complete)
        assertEquals(41L, result.bytes)
        assertArrayEquals(originalBytes, Files.readAllBytes(stagedApk))
        assertEquals(43L, Files.size(cacheDirectory.resolve("updates-backup/old.apk")))
        assertEquals(47L, Files.size(cacheDirectory.resolve("downloads/movie.mkv")))
    }

    @Test fun compiledAddonDexCacheCountsWithoutIncludingOtherCodeOrDurableFiles() = fixture { root ->
        val cacheDirectory = root.resolve("cache").also(Files::createDirectories)
        val filesDirectory = root.resolve("files").also(Files::createDirectories)
        val codeCacheDirectory = root.resolve("code-cache").also(Files::createDirectories)
        write(codeCacheDirectory, "cs_dex_cache/addon/classes.dex", 37)
        write(codeCacheDirectory, "other-code/compiled.dex", 43)
        write(filesDirectory, "cs_extensions/addon.cs3", 47)
        write(filesDirectory, "datastore/account.preferences_pb", 53)

        val result = measureTvMediaCacheOccupancy(cacheDirectory, filesDirectory, codeCacheDirectory)

        assertTrue(result.complete)
        assertEquals(37L, result.bytes)
        assertEquals(43L, Files.size(codeCacheDirectory.resolve("other-code/compiled.dex")))
        assertEquals(47L, Files.size(filesDirectory.resolve("cs_extensions/addon.cs3")))
        assertEquals(53L, Files.size(filesDirectory.resolve("datastore/account.preferences_pb")))
    }

    @Test fun pluginCodeRootDiscoveryUsesExactProfileDirectoryNamesAndReportsBounds() = fixture { root ->
        listOf("plugin_code", "plugin_code_p2", "plugin_code_p17", "plugin_code_p2_backup", "plugin_code_p")
            .forEach { Files.createDirectories(root.resolve(it)) }

        val result = discoverPluginCodeCacheRoots(root)

        assertTrue(result.complete)
        assertEquals(listOf(listOf("plugin_code"), listOf("plugin_code_p17"), listOf("plugin_code_p2")), result.roots.sortedBy { it.single() })
        val bounded = discoverPluginCodeCacheRoots(root, maxEntries = 1)
        assertFalse(bounded.complete)
        assertTrue(bounded.roots.size <= 1)
    }

    @Test fun playbackStreamCachesAndStaleVodFoldersCountTowardAutoOccupancy() = fixture { root ->
        val cacheDirectory = root.resolve("cache").also(Files::createDirectories)
        val filesDirectory = root.resolve("files").also(Files::createDirectories)
        write(cacheDirectory, "nuvio_vod_cache/current-span", 37)
        write(cacheDirectory, "nuvio_vod_cache_stale_1761582000/old-span", 19)
        write(cacheDirectory, "nuvio-engine/payload/chunk", 23)
        write(filesDirectory, "scene-bookmarks-v1/bookmarks.json", 11)
        write(filesDirectory, "downloads/movie.mkv", 31)

        val result = measureTvMediaCacheOccupancy(cacheDirectory, filesDirectory)

        assertTrue(result.complete)
        assertEquals(79L, result.bytes)
        assertEquals(11L, Files.size(filesDirectory.resolve("scene-bookmarks-v1/bookmarks.json")))
        assertEquals(31L, Files.size(filesDirectory.resolve("downloads/movie.mkv")))
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
