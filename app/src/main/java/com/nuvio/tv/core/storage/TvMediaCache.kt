package com.nuvio.tv.core.storage

import android.content.Context
import android.os.StatFs
import coil3.request.CachePolicy
import com.nuvio.tv.core.runtime.DeviceCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

fun tvMediaCache(context: Context): TvMediaCache =
    (context.applicationContext as? com.nuvio.tv.NuvioApplication)?.mediaCache ?: TvMediaCache(context.applicationContext)

/** Device-owned settings. Never synchronized through the account/profile transport. */
@Singleton
class TvMediaCache @Inject constructor(@param:ApplicationContext context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("telumia_media_cache_v1", Context.MODE_PRIVATE)
    val activeSettings: MediaCacheSettings by lazy { loadSettings() }
    private val budgetMonitor by lazy {
        MediaCacheBudgetMonitor(MediaCachePlatform.TV, { activeSettings }, ::sampleDeviceSnapshot)
    }
    internal val deviceSnapshot: MediaCacheDeviceSnapshot get() = budgetMonitor.current().device
    val activeBudget: MediaCacheBudget get() = budgetMonitor.current().budget

    private fun sampleDeviceSnapshot(): MediaCacheDeviceSnapshot {
        val memory = DeviceCapabilities.memory(app).totalMemoryBytes
        val free = runCatching { StatFs(app.cacheDir.absolutePath).availableBytes }.getOrNull()
        return MediaCacheDeviceSnapshot(memory, free, occupiedCache.bytes)
    }
    internal val occupiedCache: MediaCacheOccupancy by lazy {
        OwnedMediaCacheOccupancy.measure(
            app.cacheDir.toPath(),
            listOf(
                listOf("image_cache"), listOf("badge_cache"), listOf("live_tv_guide"), listOf("metadata"),
                listOf("http_cache_v2"), listOf("addon_http_cache"),
                // Legacy trust-all/plugin cache: count bytes without opening or migrating entries.
                listOf("http_cache"), listOf("member_profile_avatars"),
            ),
        )
    }
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

    fun coilQuota(category: MediaCacheCategory): Long = cacheQuota(category)
}
