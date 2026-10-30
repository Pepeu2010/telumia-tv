package com.nuvio.tv.data.simkl

import com.nuvio.tv.core.runtime.DeviceResourceTier

/** Initial retained-entry budgets for Simkl ID and episode-mapping responses. */
internal data class SimklIdResolverCacheLimits(
    val resolvedIds: Int,
    val episodeMappings: Int,
    val animeSeasons: Int,
) {
    init {
        require(resolvedIds > 0 && episodeMappings > 0 && animeSeasons > 0) {
            "Simkl ID cache limits must be positive"
        }
    }

    companion object {
        fun forTier(tier: DeviceResourceTier): SimklIdResolverCacheLimits = when (tier) {
            DeviceResourceTier.LOW_RAM, DeviceResourceTier.UNKNOWN -> SimklIdResolverCacheLimits(
                resolvedIds = 24,
                episodeMappings = 4,
                animeSeasons = 8,
            )
            DeviceResourceTier.BALANCED -> SimklIdResolverCacheLimits(
                resolvedIds = 48,
                episodeMappings = 8,
                animeSeasons = 16,
            )
            DeviceResourceTier.HIGH_CAPACITY -> SimklIdResolverCacheLimits(
                resolvedIds = 96,
                episodeMappings = 16,
                animeSeasons = 32,
            )
        }
    }
}
