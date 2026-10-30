package com.nuvio.tv.data.repository

import com.nuvio.tv.core.runtime.DeviceResourceTier
import org.junit.Assert.assertEquals
import org.junit.Test

class RelatedServiceCacheLimitsTest {
    @Test
    fun `low ram and unknown devices use the conservative initial budget`() {
        assertEquals(4, RelatedServiceCacheLimits.entriesPerService(DeviceResourceTier.LOW_RAM))
        assertEquals(4, RelatedServiceCacheLimits.entriesPerService(DeviceResourceTier.UNKNOWN))
    }

    @Test
    fun `balanced devices use the middle initial budget`() {
        assertEquals(8, RelatedServiceCacheLimits.entriesPerService(DeviceResourceTier.BALANCED))
    }

    @Test
    fun `high capacity devices use the largest initial budget`() {
        assertEquals(16, RelatedServiceCacheLimits.entriesPerService(DeviceResourceTier.HIGH_CAPACITY))
    }
}
