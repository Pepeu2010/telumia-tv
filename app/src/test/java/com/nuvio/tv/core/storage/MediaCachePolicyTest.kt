package com.nuvio.tv.core.storage

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class MediaCachePolicyTest {
    @Test fun unknownDevicesUseInitialDefaultsAndKnownCapabilitiesAdaptAuto() {
        assertEquals(1024L * MIB, MediaCachePolicy.resolve(MediaCachePlatform.DESKTOP, MediaCacheSettings(), MediaCacheDeviceSnapshot()).totalBytes)
        assertEquals(256L * MIB, MediaCachePolicy.resolve(MediaCachePlatform.TV, MediaCacheSettings(), MediaCacheDeviceSnapshot()).totalBytes)
        assertEquals(2048L * MIB, MediaCachePolicy.resolve(MediaCachePlatform.DESKTOP, MediaCacheSettings(), MediaCacheDeviceSnapshot(memoryBytes = 16384L * MIB)).totalBytes)
        assertEquals(128L * MIB, MediaCachePolicy.resolve(MediaCachePlatform.TV, MediaCacheSettings(), MediaCacheDeviceSnapshot(memoryBytes = 1024L * MIB)).totalBytes)
    }

    @Test fun manualLimitsCanExceedTheInitialDefaultsButRespectStorageReserve() {
        val settings = MediaCacheSettings(MediaCacheMode.MANUAL, 4096L * MIB)
        val budget = MediaCachePolicy.resolve(MediaCachePlatform.DESKTOP, settings, MediaCacheDeviceSnapshot(usableStorageBytes = 8192L * MIB))
        assertEquals(4096L * MIB, budget.totalBytes)
        val low = MediaCachePolicy.resolve(MediaCachePlatform.TV, settings, MediaCacheDeviceSnapshot(usableStorageBytes = 300L * MIB))
        assertEquals(44L * MIB, low.totalBytes)
        assertTrue(MediaCacheConstraint.STORAGE_RESERVE in low.constraints)
    }

    @Test fun existingCacheDoesNotCauseAutoQuotaToCollapseAfterEachWrite() {
        val before = MediaCachePolicy.resolve(MediaCachePlatform.TV, MediaCacheSettings(), MediaCacheDeviceSnapshot(usableStorageBytes = 1024L * MIB))
        val after = MediaCachePolicy.resolve(MediaCachePlatform.TV, MediaCacheSettings(), MediaCacheDeviceSnapshot(usableStorageBytes = 924L * MIB, occupiedCacheBytes = 100L * MIB))
        assertEquals(before.totalBytes, after.totalBytes)
        assertEquals(0L, MediaCachePolicy.resolve(MediaCachePlatform.TV, MediaCacheSettings(), MediaCacheDeviceSnapshot(usableStorageBytes = 0)).totalBytes)
    }

    @Test fun occupiedCacheNeverOverridesTheRealFreeSpaceReserve() {
        for (platform in MediaCachePlatform.entries) for (mode in MediaCacheMode.entries) {
            for (free in listOf(0L, platform.reserveBytes - 1, platform.reserveBytes)) {
                val budget = MediaCachePolicy.resolve(platform, MediaCacheSettings(mode, 4096L * MIB),
                    MediaCacheDeviceSnapshot(usableStorageBytes = free, occupiedCacheBytes = Long.MAX_VALUE))
                assertEquals(0L, budget.totalBytes)
                assertTrue(MediaCacheConstraint.STORAGE_RESERVE in budget.constraints)
                assertTrue(budget.requestedBytes > 0)
            }
        }
    }

    @Test fun malformedSettingsAndExtremeTelemetryCannotOverflowOrOverallocate() {
        val budget = MediaCachePolicy.resolve(MediaCachePlatform.DESKTOP, MediaCacheSettings(MediaCacheMode.MANUAL, Long.MAX_VALUE),
            MediaCacheDeviceSnapshot(usableStorageBytes = Long.MAX_VALUE, occupiedCacheBytes = Long.MAX_VALUE))
        assertEquals(MediaCachePolicy.MAX_CONFIGURED_BYTES, budget.totalBytes)
        assertTrue(MediaCacheCategory.entries.sumOf { budget.quota(it) } <= budget.totalBytes)
        assertEquals(32L * MIB, MediaCachePolicy.resolve(MediaCachePlatform.TV, MediaCacheSettings(MediaCacheMode.MANUAL, -1), MediaCacheDeviceSnapshot()).totalBytes)
    }

    @Test fun autoBudgetRefreshesStorageDuringTheSessionAndPausesWritesBelowAnOpenCacheQuota() {
        var now = 10L
        var freeBytes = 20_000L * MIB
        var samples = 0
        val monitor = MediaCacheBudgetMonitor(
            platform = MediaCachePlatform.TV,
            settings = { MediaCacheSettings() },
            sampleDevice = { samples++; MediaCacheDeviceSnapshot(usableStorageBytes = freeBytes) },
            clockMillis = { now },
            refreshIntervalMillis = 15_000L,
        )
        val opened = monitor.current()
        val openedQuota = opened.budget.quota(MediaCacheCategory.HTTP)
        assertTrue(MediaCachePolicy.allowsNewWrites(opened.budget, MediaCacheCategory.HTTP, openedQuota))

        freeBytes = 300L * MIB
        now += 15_000L
        val refreshed = monitor.current()
        assertEquals(2, samples)
        assertEquals(freeBytes, refreshed.device.usableStorageBytes)
        assertTrue(refreshed.budget.totalBytes > 0 && refreshed.budget.totalBytes < opened.budget.totalBytes)
        assertFalse(MediaCachePolicy.allowsNewWrites(refreshed.budget, MediaCacheCategory.HTTP, openedQuota))
        assertEquals(refreshed, monitor.current())
    }

    @Test fun occupancyUpdatesCanInvalidateTheBudgetBeforeItsRegularTtlExpires() {
        var samples = 0
        val monitor = MediaCacheBudgetMonitor(
            platform = MediaCachePlatform.TV,
            settings = { MediaCacheSettings() },
            sampleDevice = { samples++; MediaCacheDeviceSnapshot(usableStorageBytes = 20_000L * MIB) },
            clockMillis = { 1L },
            refreshIntervalMillis = 15_000L,
        )

        monitor.current()
        assertEquals(1, samples)
        monitor.invalidate()
        monitor.current()
        assertEquals(2, samples)
    }

    @Test fun avatarEntryWritesRespectCategoryQuotaAndThePhysicalStorageReserve() {
        val budget = MediaCachePolicy.resolve(
            MediaCachePlatform.TV,
            MediaCacheSettings(),
            MediaCacheDeviceSnapshot(),
        )
        assertTrue(MediaCachePolicy.canBeginCacheWrite(
            budget, MediaCacheCategory.AVATARS, MediaCachePlatform.TV.reserveBytes + 1, MediaCachePlatform.TV.reserveBytes,
        ))
        assertTrue(MediaCachePolicy.allowsCacheEntry(
            budget, MediaCacheCategory.AVATARS, 1, MediaCachePlatform.TV.reserveBytes + 1, MediaCachePlatform.TV.reserveBytes,
        ))
        assertFalse(MediaCachePolicy.allowsCacheEntry(
            budget, MediaCacheCategory.AVATARS, 2, MediaCachePlatform.TV.reserveBytes + 1, MediaCachePlatform.TV.reserveBytes,
        ))
        assertFalse(MediaCachePolicy.allowsCacheEntry(
            budget, MediaCacheCategory.AVATARS, 1, null, MediaCachePlatform.TV.reserveBytes,
        ))
        val emptyQuota = budget.copy(totalBytes = 0)
        assertFalse(MediaCachePolicy.canBeginCacheWrite(
            emptyQuota, MediaCacheCategory.AVATARS, MediaCachePlatform.TV.reserveBytes + 1, MediaCachePlatform.TV.reserveBytes,
        ))
    }

    @Test fun networkCacheWritesRequireTheOpenedQuotaAndFreshStorageReserve() {
        val budget = MediaCachePolicy.resolve(MediaCachePlatform.TV, MediaCacheSettings(), MediaCacheDeviceSnapshot())
        val openedQuota = budget.quota(MediaCacheCategory.HTTP)
        val reserve = MediaCachePlatform.TV.reserveBytes

        assertTrue(MediaCachePolicy.allowsNetworkCacheWrite(budget, MediaCacheCategory.HTTP, openedQuota, reserve + 1, reserve))
        assertFalse(MediaCachePolicy.allowsNetworkCacheWrite(budget, MediaCacheCategory.HTTP, openedQuota, reserve, reserve))
        assertFalse(MediaCachePolicy.allowsNetworkCacheWrite(budget, MediaCacheCategory.HTTP, openedQuota, null, reserve))
        assertFalse(MediaCachePolicy.allowsNetworkCacheWrite(budget, MediaCacheCategory.HTTP, openedQuota + 1, reserve + 1, reserve))
    }

    @Test fun categorySharesUseTheWholeBudgetExactlyOnce() {
        assertEquals(100, MediaCacheCategory.entries.sumOf { it.share })
        val budget = MediaCachePolicy.resolve(
            MediaCachePlatform.TV,
            MediaCacheSettings(),
            MediaCacheDeviceSnapshot(),
        )
        val allocated = MediaCacheCategory.entries.sumOf { budget.quota(it) }
        assertTrue(allocated <= budget.totalBytes)
        assertTrue(budget.totalBytes - allocated < 100)
    }

    @Test fun subtitlesHaveTheirOwnSmallShareOfTheConfigurableMediaBudget() {
        assertEquals(38, MediaCacheCategory.IMAGES.share)
        val subtitles = MediaCacheCategory.entries.singleOrNull { it.name == "SUBTITLES" }
        assertEquals(2, subtitles?.share)
        val budget = MediaCachePolicy.resolve(MediaCachePlatform.TV, MediaCacheSettings(), MediaCacheDeviceSnapshot())
        assertEquals(budget.totalBytes / 100 * 2, budget.quota(subtitles!!))
    }

    @Test fun coilAndProfileBackgroundCachesShareTheImagesQuota() {
        val desktop = MediaImageCachePolicy.allocate(1_000L, hasSeparateGifCache = true)
        assertEquals(800L, desktop.coilBytes)
        assertEquals(100L, desktop.gifBytes)
        assertEquals(100L, desktop.memberBackgroundBytes)

        val tv = MediaImageCachePolicy.allocate(1_000L, hasSeparateGifCache = false)
        assertEquals(900L, tv.coilBytes)
        assertEquals(0L, tv.gifBytes)
        assertEquals(100L, tv.memberBackgroundBytes)
    }

    @Test fun openedCoilCacheStopsWritesWhenAutoShrinksItsImageSubquota() {
        val openedBudget = MediaCacheBudget(1_000L, 1_000L, emptySet())
        val openedQuota = MediaImageCachePolicy.allocate(
            openedBudget.quota(MediaCacheCategory.IMAGES), hasSeparateGifCache = false,
        ).coilBytes
        val refreshedBudget = openedBudget.copy(totalBytes = 950L)
        val refreshedCategoryQuota = refreshedBudget.quota(MediaCacheCategory.IMAGES)
        val refreshedCoilQuota = MediaImageCachePolicy.allocate(
            refreshedCategoryQuota, hasSeparateGifCache = false,
        ).coilBytes

        assertTrue(refreshedCategoryQuota >= openedQuota)
        assertTrue(refreshedCoilQuota < openedQuota)
        assertTrue(MediaCachePolicy.allowsNewWrites(refreshedBudget, MediaCacheCategory.IMAGES, openedQuota))
        assertFalse(MediaCachePolicy.allowsNewWrites(
            refreshedBudget, MediaCacheCategory.IMAGES, openedQuota, refreshedCoilQuota,
        ))
        assertFalse(MediaCachePolicy.allowsNewWrites(
            refreshedBudget, MediaCacheCategory.IMAGES, refreshedCategoryQuota + 1L, Long.MAX_VALUE,
        ))
    }
}
