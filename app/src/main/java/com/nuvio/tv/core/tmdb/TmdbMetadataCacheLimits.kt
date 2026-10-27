package com.nuvio.tv.core.tmdb

import com.nuvio.tv.core.runtime.DeviceResourceTier

/** Initial per-cache entry budgets, scaled conservatively to the device's reported memory tier. */
data class TmdbMetadataCacheLimits(
    val enrichment: Int,
    val episodes: Int,
    val people: Int,
    val recommendations: Int,
    val entityHeaders: Int,
    val entityRails: Int,
    val entityBrowse: Int,
) {
    init {
        require(listOf(enrichment, episodes, people, recommendations, entityHeaders, entityRails, entityBrowse)
            .all { it > 0 }) { "Metadata cache limits must be positive" }
    }

    companion object {
        fun forTier(tier: DeviceResourceTier): TmdbMetadataCacheLimits = when (tier) {
            DeviceResourceTier.LOW_RAM, DeviceResourceTier.UNKNOWN -> TmdbMetadataCacheLimits(
                enrichment = 12,
                episodes = 8,
                people = 12,
                recommendations = 6,
                entityHeaders = 12,
                entityRails = 6,
                entityBrowse = 6,
            )
            DeviceResourceTier.BALANCED -> TmdbMetadataCacheLimits(
                enrichment = 24,
                episodes = 16,
                people = 24,
                recommendations = 12,
                entityHeaders = 24,
                entityRails = 12,
                entityBrowse = 12,
            )
            DeviceResourceTier.HIGH_CAPACITY -> TmdbMetadataCacheLimits(
                enrichment = 48,
                episodes = 32,
                people = 48,
                recommendations = 24,
                entityHeaders = 48,
                entityRails = 24,
                entityBrowse = 24,
            )
        }
    }
}
