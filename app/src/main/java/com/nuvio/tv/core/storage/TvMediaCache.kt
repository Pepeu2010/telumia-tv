package com.nuvio.tv.core.storage

import android.content.Context
import android.os.StatFs
import coil3.request.CachePolicy
import com.nuvio.tv.core.runtime.DeviceCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cache
import java.io.File
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

internal const val TV_VOD_CACHE_DIRECTORY_NAME = "nuvio_vod_cache"
internal const val TV_VOD_CACHE_STALE_PREFIX = "nuvio_vod_cache_stale_"
internal const val TV_TORRENT_PAYLOAD_CACHE_PATH = "nuvio-engine/payload"
private const val MAX_STALE_VOD_CACHE_ROOTS = 128
private const val MAX_STALE_VOD_DIRECTORY_ENTRIES = 16_384
private const val STALE_VOD_DISCOVERY_TIMEOUT_NANOS = 10_000_000L
private const val MAX_PLUGIN_CODE_CACHE_ROOTS = 128
private const val MAX_PLUGIN_CODE_DIRECTORY_ENTRIES = 16_384
private const val PLUGIN_CODE_DISCOVERY_TIMEOUT_NANOS = 10_000_000L

private data class DiscoveredStaleVodCaches(val roots: List<List<String>>, val complete: Boolean)
internal data class DiscoveredPluginCodeCaches(val roots: List<List<String>>, val complete: Boolean)

private fun discoverStaleVodCacheRoots(cacheDirectory: Path): DiscoveredStaleVodCaches {
    val names = mutableListOf<String>()
    var complete = true
    val startedAtNanos = System.nanoTime()
    var visitedEntries = 0
    try {
        Files.newDirectoryStream(cacheDirectory, DirectoryStream.Filter { path ->
            path.fileName?.toString()?.startsWith(TV_VOD_CACHE_STALE_PREFIX) == true
        }).use { entries ->
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                if (visitedEntries >= MAX_STALE_VOD_DIRECTORY_ENTRIES ||
                    System.nanoTime() - startedAtNanos >= STALE_VOD_DISCOVERY_TIMEOUT_NANOS ||
                    names.size >= MAX_STALE_VOD_CACHE_ROOTS
                ) {
                    complete = false
                    break
                }
                visitedEntries++
                val name = iterator.next().fileName?.toString().orEmpty()
                if (name.length > TV_VOD_CACHE_STALE_PREFIX.length &&
                    name != "." && name != ".." && '/' !in name && '\\' !in name
                ) {
                    names += name
                }
            }
        }
    } catch (_: NoSuchFileException) {
        // A fresh installation has no cache directory yet.
    } catch (_: IOException) {
        complete = false
    } catch (_: DirectoryIteratorException) {
        complete = false
    } catch (_: SecurityException) {
        complete = false
    }
    return DiscoveredStaleVodCaches(names.sorted().map { listOf(it) }, complete)
}

/** Plugin scraper scripts are regenerated from configured repositories, but live in filesDir. */
internal fun discoverPluginCodeCacheRoots(
    filesDirectory: Path,
    maxEntries: Int = MAX_PLUGIN_CODE_DIRECTORY_ENTRIES,
    maxRoots: Int = MAX_PLUGIN_CODE_CACHE_ROOTS,
    maxDurationNanos: Long = PLUGIN_CODE_DISCOVERY_TIMEOUT_NANOS,
): DiscoveredPluginCodeCaches {
    require(maxEntries > 0 && maxRoots > 0 && maxDurationNanos > 0)
    val names = mutableListOf<String>()
    var complete = true
    val startedAtNanos = System.nanoTime()
    var visitedEntries = 0
    try {
        Files.newDirectoryStream(filesDirectory).use { entries ->
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                if (visitedEntries >= maxEntries ||
                    System.nanoTime() - startedAtNanos >= maxDurationNanos ||
                    names.size >= maxRoots
                ) {
                    complete = false
                    break
                }
                visitedEntries++
                val name = iterator.next().fileName?.toString().orEmpty()
                val profileSuffix = name.removePrefix("plugin_code_p")
                val isProfileCache = name.startsWith("plugin_code_p") && profileSuffix.isNotEmpty() &&
                    profileSuffix.all { it in '0'..'9' }
                if (name == "plugin_code" || isProfileCache) names += name
            }
        }
    } catch (_: NoSuchFileException) {
        // A fresh installation has no files directory yet.
    } catch (_: IOException) {
        complete = false
    } catch (_: DirectoryIteratorException) {
        complete = false
    } catch (_: SecurityException) {
        complete = false
    }
    return DiscoveredPluginCodeCaches(names.distinct().sorted().map { listOf(it) }, complete)
}

fun tvMediaCache(context: Context): TvMediaCache =
    (context.applicationContext as? com.nuvio.tv.NuvioApplication)?.mediaCache ?: TvMediaCache(context.applicationContext)

internal fun measureTvMediaCacheOccupancy(cacheDirectory: Path, filesDirectory: Path): MediaCacheOccupancy {
    val staleVodCaches = discoverStaleVodCacheRoots(cacheDirectory)
    val pluginCodeCaches = discoverPluginCodeCacheRoots(filesDirectory)
    val currentCaches = OwnedMediaCacheOccupancy.measure(
        cacheDirectory,
        listOf(
            listOf("image_cache"), listOf("badge_cache"), listOf("live_tv_guide"), listOf("metadata"),
            listOf("media-cache-v1", "previews"),
            listOf("http_cache_v2"), listOf("addon_http_cache"),
            // Legacy trust-all/plugin cache: count bytes without opening or migrating entries.
            listOf("http_cache"), listOf("member_profile_avatars"),
            listOf("member_profile_backgrounds"), listOf("member_profile_backgrounds_v2"), listOf("subtitles"),
            listOf(TV_VOD_CACHE_DIRECTORY_NAME), TV_TORRENT_PAYLOAD_CACHE_PATH.split('/'),
        ) + staleVodCaches.roots,
    )
    val legacyFilesCaches = OwnedMediaCacheOccupancy.measure(
        filesDirectory,
        listOf(listOf("mdblist_cache"), listOf("cw_enrichment")) + pluginCodeCaches.roots,
    )
    val totalBytes = if (currentCaches.bytes > Long.MAX_VALUE - legacyFilesCaches.bytes) {
        Long.MAX_VALUE
    } else {
        currentCaches.bytes + legacyFilesCaches.bytes
    }
    val visitedEntries = (currentCaches.visitedEntries.toLong() + legacyFilesCaches.visitedEntries)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    return MediaCacheOccupancy(
        totalBytes,
        visitedEntries,
        currentCaches.complete && legacyFilesCaches.complete && staleVodCaches.complete && pluginCodeCaches.complete,
    )
}

/** Device-owned settings. Never synchronized through the account/profile transport. */
@Singleton
class TvMediaCache @Inject constructor(@param:ApplicationContext context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("telumia_media_cache_v1", Context.MODE_PRIVATE)
    private val addonHttpCacheState by lazy {
        val openedQuotaBytes = cacheQuota(MediaCacheCategory.ADDONS)
        Cache(File(app.cacheDir, "addon_http_cache"), openedQuotaBytes) to openedQuotaBytes
    }
    private val occupancyExecutor by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "telumia-media-cache-scan").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }
    private val occupancyMonitor by lazy {
        MediaCacheOccupancyMonitor(
            scan = { measureTvMediaCacheOccupancy(app.cacheDir.toPath(), app.filesDir.toPath()) },
            executor = occupancyExecutor,
            onSnapshotUpdated = budgetMonitor::invalidate,
        )
    }
    val activeSettings: MediaCacheSettings by lazy { loadSettings() }
    private val budgetMonitor = MediaCacheBudgetMonitor(
        MediaCachePlatform.TV,
        { activeSettings },
        ::sampleDeviceSnapshot,
    )
    internal val deviceSnapshot: MediaCacheDeviceSnapshot get() = budgetMonitor.current().device
    val activeBudget: MediaCacheBudget get() = budgetMonitor.current().budget

    private fun sampleDeviceSnapshot(): MediaCacheDeviceSnapshot {
        val memory = DeviceCapabilities.memory(app).totalMemoryBytes
        val free = runCatching { StatFs(app.cacheDir.absolutePath).availableBytes }.getOrNull()
        return MediaCacheDeviceSnapshot(memory, free, occupiedCache.bytes)
    }
    internal val occupiedCache: MediaCacheOccupancy get() = occupancyMonitor.current()
    val writesEnabled: Boolean get() = activeBudget.totalBytes > 0
    fun cacheWritesAllowed(category: MediaCacheCategory, openedQuotaBytes: Long): Boolean =
        MediaCachePolicy.allowsNewWrites(activeBudget, category, openedQuotaBytes)

    fun canBeginCacheWrite(category: MediaCacheCategory): Boolean = MediaCachePolicy.canBeginCacheWrite(
        activeBudget, category, availableStorageBytes(), MediaCachePlatform.TV.reserveBytes,
    )

    fun canWriteCacheEntry(category: MediaCacheCategory, entryBytes: Long): Boolean = MediaCachePolicy.allowsCacheEntry(
        activeBudget, category, entryBytes, availableStorageBytes(), MediaCachePlatform.TV.reserveBytes,
    )

    private fun availableStorageBytes(): Long? =
        runCatching { StatFs(app.cacheDir.absolutePath).availableBytes }.getOrNull()
    val requestPolicy: CachePolicy get() = if (writesEnabled) CachePolicy.ENABLED else CachePolicy.READ_ONLY

    fun loadSettings(): MediaCacheSettings = runCatching {
        if (preferences.getInt("schema", 1) != 1) return@runCatching MediaCacheSettings()
        val mode = MediaCacheMode.entries.firstOrNull { it.name == preferences.getString("mode", "AUTO") } ?: MediaCacheMode.AUTO
        val manual = if (preferences.contains("manual_bytes")) preferences.getLong("manual_bytes", MediaCachePlatform.TV.initialBytes)
            .coerceIn(32L * MIB, MediaCachePolicy.MAX_CONFIGURED_BYTES) else null
        MediaCacheSettings(mode, manual)
    }.getOrDefault(MediaCacheSettings())

    @Synchronized fun saveSettings(settings: MediaCacheSettings): Boolean {
        if (runCatching { preferences.getInt("schema", 1) }.getOrNull() != 1) return false
        val edit = preferences.edit().putInt("schema", 1).putString("mode", settings.mode.name)
        if (settings.manualBytes == null) edit.remove("manual_bytes")
        else edit.putLong("manual_bytes", settings.manualBytes.coerceIn(32L * MIB, MediaCachePolicy.MAX_CONFIGURED_BYTES))
        return edit.commit()
    }

    /** Read-only opening retains offline entries when storage drops below the reserve. */
    fun cacheQuota(category: MediaCacheCategory): Long {
        val budget = if (writesEnabled) activeBudget else activeBudget.copy(totalBytes = activeBudget.requestedBytes)
        return budget.quota(category)
    }

    /** One shared OkHttp disk cache for every addon transport on this device. */
    fun addonHttpCache(): Cache = addonHttpCacheState.first

    /** A cache opened at an earlier quota stops accepting network writes after Auto shrinks. */
    fun addonHttpCacheWritesAllowed(): Boolean =
        cacheWritesAllowed(MediaCacheCategory.ADDONS, addonHttpCacheState.second)

    private fun imageCacheAllocation() = MediaImageCachePolicy.allocate(
        cacheQuota(MediaCacheCategory.IMAGES), hasSeparateGifCache = false,
    )
    fun coilQuota(category: MediaCacheCategory): Long =
        if (category == MediaCacheCategory.IMAGES) imageCacheAllocation().coilBytes else cacheQuota(category)
    fun profileBackgroundQuota(): Long = imageCacheAllocation().memberBackgroundBytes
}
