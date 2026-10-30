package com.nuvio.tv.data.repository

import com.nuvio.tv.core.runtime.DeviceResourceTier
import org.junit.Assert.assertEquals
import org.junit.Test

class TraktEpisodeMappingCacheLimitsTest {
    @Test
    fun `low ram and unknown use conservative capacities`() {
        val expected = TraktEpisodeMappingCacheLimits(
            remappedEpisodes = 96,
            addonEpisodeLists = 4,
            traktEpisodeLists = 4,
            reverseRemaps = 96,
            normalizedEpisodeTitles = 512,
        )

        assertEquals(expected, TraktEpisodeMappingCacheLimits.forTier(DeviceResourceTier.LOW_RAM))
        assertEquals(expected, TraktEpisodeMappingCacheLimits.forTier(DeviceResourceTier.UNKNOWN))
    }

    @Test
    fun `balanced tier uses middle capacities`() {
        assertEquals(
            TraktEpisodeMappingCacheLimits(192, 8, 8, 192, 1_024),
            TraktEpisodeMappingCacheLimits.forTier(DeviceResourceTier.BALANCED),
        )
    }

    @Test
    fun `high capacity tier gets larger list budgets`() {
        assertEquals(
            TraktEpisodeMappingCacheLimits(384, 16, 16, 384, 2_048),
            TraktEpisodeMappingCacheLimits.forTier(DeviceResourceTier.HIGH_CAPACITY),
        )
    }
}
