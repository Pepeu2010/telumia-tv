package com.nuvio.tv.data.trailer

import com.nuvio.tv.core.runtime.DeviceResourceTier

/** Initial retained-entry budgets for trailer search results and resolved YouTube sources. */
data class TrailerCacheLimits(
    val searchResults: Int,
    val youtubePlaybackSources: Int,
) {
    init {
        require(searchResults > 0 && youtubePlaybackSources > 0) {
            "Trailer cache limits must be positive"
        }
    }

    companion object {
        fun forTier(tier: DeviceResourceTier): TrailerCacheLimits = when (tier) {
            DeviceResourceTier.LOW_RAM, DeviceResourceTier.UNKNOWN -> TrailerCacheLimits(
                searchResults = 12,
                youtubePlaybackSources = 6,
            )
            DeviceResourceTier.BALANCED -> TrailerCacheLimits(
                searchResults = 24,
                youtubePlaybackSources = 12,
            )
            DeviceResourceTier.HIGH_CAPACITY -> TrailerCacheLimits(
                searchResults = 48,
                youtubePlaybackSources = 24,
            )
        }
    }
}
