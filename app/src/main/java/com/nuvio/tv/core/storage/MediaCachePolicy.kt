package com.nuvio.tv.core.storage

enum class MediaCachePlatform(val initialBytes: Long, val reserveBytes: Long) {
    DESKTOP(1024L * MIB, 2048L * MIB), TV(256L * MIB, 256L * MIB)
}
enum class MediaCacheMode { AUTO, MANUAL }
enum class MediaCacheCategory(val directoryName: String, val share: Int) {
    IMAGES("images", 38), BADGES("badges", 4), AVATARS("avatars", 6), PREVIEWS("previews", 10),
    THUMBNAILS("thumbnails", 8), FILMSTRIP("filmstrip", 4), METADATA("metadata", 10), HTTP("http", 5),
    ADDONS("addons", 5), EPG("epg", 5), SCENE_INFO("scene-info", 3), SUBTITLES("subtitles", 2)
}
const val MIB: Long = 1024L * 1024L
data class MediaCacheSettings(val mode: MediaCacheMode = MediaCacheMode.AUTO, val manualBytes: Long? = null)
data class MediaCacheDeviceSnapshot(val memoryBytes: Long? = null, val usableStorageBytes: Long? = null, val occupiedCacheBytes: Long = 0)
enum class MediaCacheConstraint { DEVICE_MEMORY, STORAGE_RESERVE, MANUAL_LIMIT }
data class MediaCacheBudget(val totalBytes: Long, val requestedBytes: Long, val constraints: Set<MediaCacheConstraint>) {
    fun quota(category: MediaCacheCategory): Long = totalBytes / 100 * category.share
}

data class MetadataCacheAllocation(
    val ratingsBytes: Long,
    val continueWatchingBytes: Long,
    val streamLinksBytes: Long = 0L,
    val bingeGroupsBytes: Long = 0L,
)

/** The TV has two persistent consumers in METADATA; keep their combined ceiling inside the category quota. */
object MetadataCachePolicy {
    const val MAX_RATINGS_BYTES = 8L * MIB
    const val MAX_CONTINUE_WATCHING_BYTES = 8L * MIB
    const val MAX_STREAM_LINK_BYTES = 8L * MIB
    const val MAX_BINGE_GROUP_BYTES = 2L * MIB

    fun allocate(categoryQuotaBytes: Long): MetadataCacheAllocation {
        val quota = categoryQuotaBytes.coerceAtLeast(0L)
        val ratings = quota.coerceAtMost(MAX_RATINGS_BYTES)
        val continueWatching = (quota - ratings).coerceAtLeast(0L).coerceAtMost(MAX_CONTINUE_WATCHING_BYTES)
        val afterExistingConsumers = (quota - ratings - continueWatching).coerceAtLeast(0L)
        val streamLinks = (afterExistingConsumers - afterExistingConsumers / 4L)
            .coerceAtMost(MAX_STREAM_LINK_BYTES)
        val afterStreamLinks = (afterExistingConsumers - streamLinks).coerceAtLeast(0L)
        val bingeGroups = (afterStreamLinks - afterStreamLinks / 2L)
            .coerceAtMost(MAX_BINGE_GROUP_BYTES)
        return MetadataCacheAllocation(ratings, continueWatching, streamLinks, bingeGroups)
    }
}

data class MediaImageCacheAllocation(val coilBytes: Long, val gifBytes: Long, val memberBackgroundBytes: Long)

object MediaImageCachePolicy {
    /** Keeps profile backgrounds inside IMAGES without letting separate consumers each spend the full share. */
    fun allocate(imageQuotaBytes: Long, hasSeparateGifCache: Boolean): MediaImageCacheAllocation {
        val imageQuota = imageQuotaBytes.coerceAtLeast(0)
        val backgroundBytes = imageQuota / 10
        val gifBytes = if (hasSeparateGifCache) imageQuota / 10 else 0
        return MediaImageCacheAllocation(imageQuota - backgroundBytes - gifBytes, gifBytes, backgroundBytes)
    }
}

/** Re-samples device telemetry on demand, with a short TTL to avoid filesystem probes per request. */
internal class MediaCacheBudgetMonitor(
    private val platform: MediaCachePlatform,
    private val settings: () -> MediaCacheSettings,
    private val sampleDevice: () -> MediaCacheDeviceSnapshot,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val refreshIntervalMillis: Long = 15_000L,
) {
    data class Snapshot(
        val device: MediaCacheDeviceSnapshot,
        val budget: MediaCacheBudget,
        val sampledAtMillis: Long,
    )

    @Volatile private var cached: Snapshot? = null

    init { require(refreshIntervalMillis > 0) }

    fun current(forceRefresh: Boolean = false): Snapshot {
        val initial = cached
        if (!forceRefresh && initial != null && isFresh(initial, clockMillis())) return initial
        return synchronized(this) {
            val latest = cached
            if (!forceRefresh && latest != null && isFresh(latest, clockMillis())) latest
            else {
                val sampledAt = clockMillis()
                val device = runCatching(sampleDevice).getOrElse {
                    latest?.device ?: MediaCacheDeviceSnapshot()
                }
                Snapshot(device, MediaCachePolicy.resolve(platform, settings(), device), sampledAt)
                    .also { cached = it }
            }
        }
    }

    /** Forces the next reader to resample device telemetry after asynchronous occupancy changes. */
    fun invalidate() { cached = null }

    private fun isFresh(snapshot: Snapshot, nowMillis: Long): Boolean =
        nowMillis >= snapshot.sampledAtMillis && nowMillis - snapshot.sampledAtMillis < refreshIntervalMillis
}

/** Defaults are initial estimates, not permanent device ceilings. Durable user data is outside this policy. */
object MediaCachePolicy {
    const val MAX_CONFIGURED_BYTES: Long = 64L * 1024L * MIB

    /** Keep current cache hits readable while stopping writes after Auto shrinks below an open cache's cap. */
    fun allowsNewWrites(budget: MediaCacheBudget, category: MediaCacheCategory, openedQuotaBytes: Long): Boolean =
        openedQuotaBytes > 0 && budget.totalBytes > 0 && budget.quota(category) >= openedQuotaBytes

    /** Network caches have unknown response sizes, so they must retain the free-storage reserve before caching. */
    fun allowsNetworkCacheWrite(
        budget: MediaCacheBudget,
        category: MediaCacheCategory,
        openedQuotaBytes: Long,
        freeStorageBytes: Long?,
        reserveBytes: Long,
    ): Boolean = allowsNewWrites(budget, category, openedQuotaBytes) &&
        canBeginCacheWrite(budget, category, freeStorageBytes, reserveBytes)

    fun canBeginCacheWrite(
        budget: MediaCacheBudget,
        category: MediaCacheCategory,
        freeStorageBytes: Long?,
        reserveBytes: Long,
    ): Boolean = budget.totalBytes > 0 && budget.quota(category) > 0 &&
        freeStorageBytes?.let { it >= 0 && it > reserveBytes.coerceAtLeast(0) } == true

    fun allowsCacheEntry(
        budget: MediaCacheBudget,
        category: MediaCacheCategory,
        entryBytes: Long,
        freeStorageBytes: Long?,
        reserveBytes: Long,
    ): Boolean {
        if (entryBytes <= 0 || budget.totalBytes <= 0 || budget.quota(category) < entryBytes) return false
        val free = freeStorageBytes?.takeIf { it >= 0 } ?: return false
        val reserve = reserveBytes.coerceAtLeast(0)
        val required = if (reserve > Long.MAX_VALUE - entryBytes) Long.MAX_VALUE else reserve + entryBytes
        return free >= required
    }
    fun resolve(platform: MediaCachePlatform, settings: MediaCacheSettings, device: MediaCacheDeviceSnapshot): MediaCacheBudget {
        val constraints = mutableSetOf<MediaCacheConstraint>()
        val memory = device.memoryBytes?.takeIf { it > 0 }
        val multiplier = when {
            memory == null -> 1
            platform == MediaCachePlatform.TV && memory <= 1536L * MIB -> -1
            platform == MediaCachePlatform.TV && memory >= 4096L * MIB -> 2
            platform == MediaCachePlatform.DESKTOP && memory <= 4096L * MIB -> -1
            platform == MediaCachePlatform.DESKTOP && memory >= 16384L * MIB -> 2
            else -> 1
        }
        val requested = if (settings.mode == MediaCacheMode.MANUAL) {
            constraints += MediaCacheConstraint.MANUAL_LIMIT
            (settings.manualBytes ?: platform.initialBytes).coerceIn(32L * MIB, MAX_CONFIGURED_BYTES)
        } else {
            if (multiplier != 1) constraints += MediaCacheConstraint.DEVICE_MEMORY
            if (multiplier == -1) platform.initialBytes / 2 else platform.initialBytes * multiplier
        }
        val free = device.usableStorageBytes?.takeIf { it >= 0 }
        val available = free?.let {
            val occupied = device.occupiedCacheBytes.coerceAtLeast(0)
            val combined = if (it > Long.MAX_VALUE - occupied) Long.MAX_VALUE else it + occupied
            (combined - platform.reserveBytes).coerceAtLeast(0)
        }
        val storageCeiling = available?.let { if (settings.mode == MediaCacheMode.AUTO) it / 10 else it }
        // Occupied cache stabilizes the estimate, but cannot grant writes below the real free-space reserve.
        val effective = if (free != null && free <= platform.reserveBytes) 0L
            else storageCeiling?.let { requested.coerceAtMost(it) } ?: requested
        if (effective < requested) constraints += MediaCacheConstraint.STORAGE_RESERVE
        return MediaCacheBudget(effective, requested, constraints)
    }
}
