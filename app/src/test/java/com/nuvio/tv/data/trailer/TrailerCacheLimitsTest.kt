package com.nuvio.tv.data.trailer

import com.nuvio.tv.core.runtime.DeviceResourceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerCacheLimitsTest {
    @Test
    fun `unknown memory uses low ram entry budgets`() {
        assertEquals(
            TrailerCacheLimits.forTier(DeviceResourceTier.LOW_RAM),
            TrailerCacheLimits.forTier(DeviceResourceTier.UNKNOWN),
        )
    }

    @Test
    fun `higher capacity tiers retain more trailer entries`() {
        val low = TrailerCacheLimits.forTier(DeviceResourceTier.LOW_RAM)
        val balanced = TrailerCacheLimits.forTier(DeviceResourceTier.BALANCED)
        val high = TrailerCacheLimits.forTier(DeviceResourceTier.HIGH_CAPACITY)

        assertTrue(low.searchResults < balanced.searchResults)
        assertTrue(balanced.searchResults < high.searchResults)
        assertTrue(low.youtubePlaybackSources < balanced.youtubePlaybackSources)
        assertTrue(balanced.youtubePlaybackSources < high.youtubePlaybackSources)
    }
}
