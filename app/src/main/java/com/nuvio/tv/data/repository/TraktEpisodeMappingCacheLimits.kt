package com.nuvio.tv.data.repository

import com.nuvio.tv.core.runtime.DeviceResourceTier

/** Initial retained-entry budgets for episode remaps and their source episode lists. */
internal data class TraktEpisodeMappingCacheLimits(
    val remappedEpisodes: Int,
    val addonEpisodeLists: Int,
    val traktEpisodeLists: Int,
    val reverseRemaps: Int,
    val normalizedEpisodeTitles: Int,
) {
    init {
        require(
            remappedEpisodes > 0 && addonEpisodeLists > 0 &&
                traktEpisodeLists > 0 && reverseRemaps > 0 && normalizedEpisodeTitles > 0,
        ) { "Trakt episode mapping cache limits must be positive" }
    }

    companion object {
        fun forTier(tier: DeviceResourceTier): TraktEpisodeMappingCacheLimits = when (tier) {
            DeviceResourceTier.LOW_RAM, DeviceResourceTier.UNKNOWN -> TraktEpisodeMappingCacheLimits(
                remappedEpisodes = 96,
                addonEpisodeLists = 4,
                traktEpisodeLists = 4,
                reverseRemaps = 96,
                normalizedEpisodeTitles = 512,
            )
            DeviceResourceTier.BALANCED -> TraktEpisodeMappingCacheLimits(
                remappedEpisodes = 192,
                addonEpisodeLists = 8,
                traktEpisodeLists = 8,
                reverseRemaps = 192,
                normalizedEpisodeTitles = 1_024,
            )
            DeviceResourceTier.HIGH_CAPACITY -> TraktEpisodeMappingCacheLimits(
                remappedEpisodes = 384,
                addonEpisodeLists = 16,
                traktEpisodeLists = 16,
                reverseRemaps = 384,
                normalizedEpisodeTitles = 2_048,
            )
        }
    }
}
