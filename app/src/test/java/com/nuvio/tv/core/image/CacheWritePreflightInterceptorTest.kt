package com.nuvio.tv.core.image

import coil3.request.CachePolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheWritePreflightInterceptorTest {
    @Test fun storageShortageKeepsExistingDiskReadsAndDisablesWrites() {
        assertEquals(CachePolicy.READ_ONLY, cachePolicyAfterStorageCheck(CachePolicy.ENABLED, canWrite = false))
    }

    @Test fun storageShortageDoesNotEnableOrDisableAnExplicitlyReadOnlyRequest() {
        assertEquals(CachePolicy.READ_ONLY, cachePolicyAfterStorageCheck(CachePolicy.READ_ONLY, canWrite = false))
    }

    @Test fun storageShortagePreservesAnExplicitlyDisabledRequest() {
        assertEquals(CachePolicy.DISABLED, cachePolicyAfterStorageCheck(CachePolicy.DISABLED, canWrite = false))
    }

    @Test fun storageShortageAlsoBlocksWriteOnlyRequestsWithoutEnablingReads() {
        assertEquals(CachePolicy.DISABLED, cachePolicyAfterStorageCheck(CachePolicy.WRITE_ONLY, canWrite = false))
    }

    @Test fun availableStoragePreservesTheRequestPolicy() {
        for (policy in CachePolicy.entries) {
            assertEquals(policy, cachePolicyAfterStorageCheck(policy, canWrite = true))
        }
    }

    @Test fun storageProbeIsSharedForOneSecondAndFailsClosedWhenSamplingThrows() = runTest {
        var now = 10L
        var allowed = true
        var samples = 0
        val sampler = StorageWritePreflightSampler(
            sample = { samples++; if (allowed) true else error("storage probe failed") },
            clockMillis = { now },
        )

        assertEquals(true, sampler.canWrite())
        assertEquals(true, sampler.canWrite())
        assertEquals(1, samples)
        now += 1_000L
        allowed = false
        assertEquals(false, sampler.canWrite())
        assertEquals(false, sampler.canWrite())
        assertEquals(2, samples)
    }
}

