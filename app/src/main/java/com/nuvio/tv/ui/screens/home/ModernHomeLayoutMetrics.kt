package com.nuvio.tv.ui.screens.home

/** Layout values shared by the TV and touch-friendly home presentations. */
internal data class ModernHomeLayoutMetrics(
    val isMobile: Boolean,
    val safeHorizontalDp: Int,
    val heroTextWidthFraction: Float,
    val rowsViewportHeightFraction: Float,
    val posterScale: Float,
    val focusEffectsEnabled: Boolean
)

internal fun modernHomeLayoutMetrics(
    screenWidthDp: Int,
    useLandscapePosters: Boolean
): ModernHomeLayoutMetrics {
    val isMobile = screenWidthDp < 600
    return if (isMobile) {
        ModernHomeLayoutMetrics(
            isMobile = true,
            safeHorizontalDp = 16,
            heroTextWidthFraction = 0.92f,
            rowsViewportHeightFraction = 0.40f,
            posterScale = 0.76f,
            focusEffectsEnabled = false
        )
    } else {
        ModernHomeLayoutMetrics(
            isMobile = false,
            safeHorizontalDp = 52,
            heroTextWidthFraction = 0.42f,
            rowsViewportHeightFraction = if (useLandscapePosters) 0.49f else 0.52f,
            posterScale = 1f,
            focusEffectsEnabled = true
        )
    }
}
