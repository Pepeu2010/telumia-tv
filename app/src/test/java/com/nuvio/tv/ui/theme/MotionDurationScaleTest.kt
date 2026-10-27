package com.nuvio.tv.ui.theme

import com.nuvio.tv.domain.model.NavigationMotion
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionDurationScaleTest {
    @Test
    fun reducedMotionShortensUnmappedComposeAnimationsAndPreservesSystemScale() {
        val scale = UiAnimationDurationScale(initialMode = NavigationMotion.FULL)
        assertEquals(1f, scale.scaleFactor, 0.001f)

        scale.mode = NavigationMotion.REDUCED
        assertEquals(0.5f, scale.scaleFactor, 0.001f)

        scale.systemScale = 0.8f
        assertEquals(0.4f, scale.scaleFactor, 0.001f)

        scale.mode = NavigationMotion.OFF
        assertEquals(0f, scale.scaleFactor, 0.001f)
    }
}
