package com.nuvio.tv.core.tmdb

import com.nuvio.tv.core.runtime.DeviceResourceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMetadataCacheLimitsTest {
    @Test
    fun `unknown memory uses conservative low ram limits`() {
        assertEquals(
            TmdbMetadataCacheLimits.forTier(DeviceResourceTier.LOW_RAM),
            TmdbMetadataCacheLimits.forTier(DeviceResourceTier.UNKNOWN),
        )
    }

    @Test
    fun `higher capacity tier allows larger metadata caches`() {
        val low = TmdbMetadataCacheLimits.forTier(DeviceResourceTier.LOW_RAM)
        val balanced = TmdbMetadataCacheLimits.forTier(DeviceResourceTier.BALANCED)
        val high = TmdbMetadataCacheLimits.forTier(DeviceResourceTier.HIGH_CAPACITY)

        assertTrue(low.enrichment < balanced.enrichment)
        assertTrue(balanced.enrichment < high.enrichment)
        assertTrue(low.episodes < balanced.episodes)
        assertTrue(balanced.episodes < high.episodes)
        assertTrue(low.people < balanced.people)
        assertTrue(low.entityRails < high.entityRails)
    }
}
