package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernHomeLayoutMetricsTest {
    @Test
    fun tvUsesWideHeroAndRemoteFocusEffects() {
        val metrics = modernHomeLayoutMetrics(
            screenWidthDp = 960,
            useLandscapePosters = false
        )

        assertFalse(metrics.isMobile)
        assertEquals(52, metrics.safeHorizontalDp)
        assertEquals(0.42f, metrics.heroTextWidthFraction)
        assertEquals(0.52f, metrics.rowsViewportHeightFraction)
        assertEquals(1f, metrics.posterScale)
        assertTrue(metrics.focusEffectsEnabled)
    }

    @Test
    fun mobileUsesFullerHeroAndSmallerStaticCards() {
        val metrics = modernHomeLayoutMetrics(
            screenWidthDp = 411,
            useLandscapePosters = true
        )

        assertTrue(metrics.isMobile)
        assertEquals(16, metrics.safeHorizontalDp)
        assertEquals(0.92f, metrics.heroTextWidthFraction)
        assertEquals(0.40f, metrics.rowsViewportHeightFraction)
        assertEquals(0.76f, metrics.posterScale)
        assertFalse(metrics.focusEffectsEnabled)
    }
}
