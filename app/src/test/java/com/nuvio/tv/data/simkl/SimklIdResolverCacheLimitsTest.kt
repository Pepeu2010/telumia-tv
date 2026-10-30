package com.nuvio.tv.data.simkl

import com.nuvio.tv.core.runtime.DeviceResourceTier
import org.junit.Assert.assertEquals
import org.junit.Test

class SimklIdResolverCacheLimitsTest {
    @Test
    fun `low ram and unknown tiers use conservative limits`() {
        val expected = SimklIdResolverCacheLimits(
            resolvedIds = 24,
            episodeMappings = 4,
            animeSeasons = 8,
        )

        assertEquals(expected, SimklIdResolverCacheLimits.forTier(DeviceResourceTier.LOW_RAM))
        assertEquals(expected, SimklIdResolverCacheLimits.forTier(DeviceResourceTier.UNKNOWN))
    }

    @Test
    fun `balanced tier uses the middle limits`() {
        assertEquals(
            SimklIdResolverCacheLimits(resolvedIds = 48, episodeMappings = 8, animeSeasons = 16),
            SimklIdResolverCacheLimits.forTier(DeviceResourceTier.BALANCED),
        )
    }

    @Test
    fun `high capacity tier gets the largest limits`() {
        assertEquals(
            SimklIdResolverCacheLimits(resolvedIds = 96, episodeMappings = 16, animeSeasons = 32),
            SimklIdResolverCacheLimits.forTier(DeviceResourceTier.HIGH_CAPACITY),
        )
    }
}
