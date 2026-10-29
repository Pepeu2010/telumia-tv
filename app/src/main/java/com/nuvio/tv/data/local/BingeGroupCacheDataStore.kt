package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.storage.BoundedPreferenceCacheEntry
import com.nuvio.tv.core.storage.MediaCacheCategory
import com.nuvio.tv.core.storage.MetadataCachePolicy
import com.nuvio.tv.core.storage.TvMediaCache
import com.nuvio.tv.core.storage.estimateShadowedPreferenceCacheBytes
import com.nuvio.tv.core.storage.BoundedPreferenceCachePolicy
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the last-used binge group per content ID (series).
 * When a user watches an episode with a stream that has a bingeGroup,
 * we store it so that subsequent episode plays (from Continue Watching,
 * Details, or Next Episode) can automatically prefer the same source group.
 *
 * Stored locally only - not synced to remote.
 */
@Singleton
class BingeGroupCacheDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager,
    private val mediaCache: TvMediaCache,
) {
    companion object {
        private const val FEATURE = "binge_group_cache"
        private const val ENTRY_PREFIX = "bg_"
        private const val TIMESTAMP_PREFIX = "bg_saved_at_"
        private const val MAX_ENTRIES_PER_PROFILE = 256
    }

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private fun prefKey(contentId: String) = stringPreferencesKey("bg_$contentId")
    private fun timestampPrefKey(contentId: String) = longPreferencesKey("$TIMESTAMP_PREFIX$contentId")

    suspend fun save(contentId: String, bingeGroup: String) {
        val key = prefKey(contentId)
        val timestampKey = timestampPrefKey(contentId)
        val now = System.currentTimeMillis().coerceAtLeast(0L)
        val perProfileQuota = MetadataCachePolicy.allocate(
            mediaCache.cacheQuota(MediaCacheCategory.METADATA),
        ).bingeGroupsBytes / ProfileManager.MAX_PROFILES
        val candidateBytes = estimateShadowedPreferenceCacheBytes(
            key.name to bingeGroup,
            timestampKey.name to now.toString(),
        )
        if (!mediaCache.writesEnabled || perProfileQuota <= 0L || candidateBytes > perProfileQuota ||
            !mediaCache.canWriteCacheEntry(MediaCacheCategory.METADATA, candidateBytes)
        ) return

        store().edit { prefs ->
            val existing = prefs.asMap().mapNotNull { (storedKey, storedValue) ->
                val name = storedKey.name
                if (!name.startsWith(ENTRY_PREFIX) || name.startsWith(TIMESTAMP_PREFIX)) return@mapNotNull null
                val value = storedValue as? String ?: return@mapNotNull null
                val storedContentId = name.removePrefix(ENTRY_PREFIX)
                val storedTimestampKey = timestampPrefKey(storedContentId)
                val storedTimestamp = prefs[storedTimestampKey]
                BoundedPreferenceCacheEntry(
                    key = name,
                    storedBytes = if (storedTimestamp == null) {
                        estimateShadowedPreferenceCacheBytes(name to value)
                    } else {
                        estimateShadowedPreferenceCacheBytes(
                            name to value,
                            storedTimestampKey.name to storedTimestamp.toString(),
                        )
                    },
                    savedAtMs = storedTimestamp ?: 0L,
                )
            }
            val candidate = BoundedPreferenceCacheEntry(
                key = key.name,
                storedBytes = candidateBytes,
                savedAtMs = now,
            )
            val retained = BoundedPreferenceCachePolicy.retain(
                entries = existing + candidate,
                maxEntries = MAX_ENTRIES_PER_PROFILE,
                maxBytes = perProfileQuota,
                protectedKey = key.name,
            )
            if (retained.none { it.key == key.name }) return@edit

            val retainedKeys = retained.mapTo(mutableSetOf()) { it.key }
            existing.filterNot { it.key in retainedKeys }.forEach { entry ->
                val removedContentId = entry.key.removePrefix(ENTRY_PREFIX)
                prefs.remove(stringPreferencesKey(entry.key))
                prefs.remove(timestampPrefKey(removedContentId))
            }
            prefs[key] = bingeGroup
            prefs[timestampKey] = now
        }
    }

    suspend fun replace(contentId: String, bingeGroup: String?) {
        val value = bingeGroup?.takeIf { it.isNotBlank() }
        if (value == null) remove(contentId) else save(contentId, value)
    }

    suspend fun get(contentId: String): String? {
        return store().data.first()[prefKey(contentId)]
    }

    suspend fun remove(contentId: String) {
        store().edit { prefs ->
            prefs.remove(prefKey(contentId))
            prefs.remove(timestampPrefKey(contentId))
        }
    }
}
