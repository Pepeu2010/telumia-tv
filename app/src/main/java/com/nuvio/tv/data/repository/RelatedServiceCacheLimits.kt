package com.nuvio.tv.data.repository

import com.nuvio.tv.core.runtime.DeviceResourceTier

/** Initial entry-count budgets per provider service, sampled from the device resource tier. */
internal object RelatedServiceCacheLimits {
    fun entriesPerService(tier: DeviceResourceTier): Int = when (tier) {
        DeviceResourceTier.LOW_RAM, DeviceResourceTier.UNKNOWN -> 4
        DeviceResourceTier.BALANCED -> 8
        DeviceResourceTier.HIGH_CAPACITY -> 16
    }
}
