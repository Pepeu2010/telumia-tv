package com.nuvio.tv.ui.components

import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.domain.model.UiMotionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusMarqueeTextTest {

    /**
     * Guards against the marquee going unbounded again: on a TV the focus can rest on the same item
     * indefinitely, and an unbounded marquee keeps the frame clock running for exactly as long. The
     * count has to stay small and finite -- enough passes to read a long label, few enough that the
     * screen falls silent on its own.
     */
    @Test
    fun `the focus marquee stops on its own`() {
        assertTrue("marquee must be bounded", MarqueeIterations in 1..5)
    }

    @Test
    fun `full motion keeps the focused rail label marquee single line`() {
        val presentation = focusMarqueePresentation(
            focused = true,
            allowsSpatialEffects = UiMotionPolicy(NavigationMotion.FULL).allowsSpatialEffects,
            wrapWhenMotionDisabled = true,
        )

        assertTrue(presentation.animate)
        assertFalse(presentation.wrapLines)
    }

    @Test
    fun `reduced and off motion keep focused rail labels static and wrapped`() {
        listOf(NavigationMotion.REDUCED, NavigationMotion.OFF).forEach { mode ->
            val policy = UiMotionPolicy(mode)
            val focused = focusMarqueePresentation(
                focused = true,
                allowsSpatialEffects = policy.allowsSpatialEffects,
                wrapWhenMotionDisabled = true,
            )
            val unfocused = focusMarqueePresentation(
                focused = false,
                allowsSpatialEffects = policy.allowsSpatialEffects,
                wrapWhenMotionDisabled = true,
            )

            assertFalse("$mode must disable the marquee", focused.animate)
            assertTrue("$mode must show the complete label on wrapped lines", focused.wrapLines)
            assertFalse("$mode must stay static when unfocused", unfocused.animate)
            assertTrue("$mode must keep the label readable when unfocused", unfocused.wrapLines)
        }
    }

    @Test
    fun `disabled motion preserves ellipsized single line behavior outside the rail`() {
        val presentation = focusMarqueePresentation(
            focused = true,
            allowsSpatialEffects = UiMotionPolicy(NavigationMotion.REDUCED).allowsSpatialEffects,
            wrapWhenMotionDisabled = false,
        )

        assertFalse(presentation.animate)
        assertFalse(presentation.wrapLines)
    }
}
