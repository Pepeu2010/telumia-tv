package com.nuvio.tv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp

@Immutable
class NuvioFocusRingStyle internal constructor(
    val solidColor: Color,
    private val gradientColors: List<Color>
) {
    private val fullBrush = createThemeBrush(gradientColors, solidColor)

    fun brush(alpha: Float = 1f): Brush {
        val normalizedAlpha = alpha.coerceIn(0f, 1f)
        if (normalizedAlpha == 1f) return fullBrush
        return createThemeBrush(
            colors = gradientColors.map { color ->
                color.copy(alpha = color.alpha * normalizedAlpha)
            },
            fallbackColor = solidColor.copy(alpha = solidColor.alpha * normalizedAlpha)
        )
    }

    fun border(width: Dp, alpha: Float = 1f): BorderStroke {
        return BorderStroke(width, brush(alpha))
    }
}

fun ThemeColorPalette.accentBrush(alpha: Float = 1f): Brush =
    createThemeBrush(accentGradient(alpha), secondary.copy(alpha = secondary.alpha * alpha.coerceIn(0f, 1f)))

fun ThemeColorPalette.accentHorizontalBrush(alpha: Float = 1f): Brush =
    createThemeBrush(
        colors = accentGradient(alpha),
        fallbackColor = secondary.copy(alpha = secondary.alpha * alpha.coerceIn(0f, 1f)),
        orientation = ThemeBrushOrientation.HORIZONTAL
    )

fun ThemeColorPalette.accentVerticalBrush(alpha: Float = 1f): Brush =
    createThemeBrush(
        colors = accentGradient(alpha),
        fallbackColor = secondary.copy(alpha = secondary.alpha * alpha.coerceIn(0f, 1f)),
        orientation = ThemeBrushOrientation.VERTICAL
    )

private fun ThemeColorPalette.accentGradient(alpha: Float): List<Color> {
    val normalizedAlpha = alpha.coerceIn(0f, 1f)
    return accentGradient.map { color -> color.copy(alpha = color.alpha * normalizedAlpha) }
}

private enum class ThemeBrushOrientation {
    LINEAR,
    HORIZONTAL,
    VERTICAL
}

private fun createThemeBrush(
    colors: List<Color>,
    fallbackColor: Color,
    orientation: ThemeBrushOrientation = ThemeBrushOrientation.LINEAR
): Brush {
    return when (colors.size) {
        0 -> SolidColor(fallbackColor)
        1 -> SolidColor(colors.first())
        else -> when (orientation) {
            ThemeBrushOrientation.LINEAR -> Brush.linearGradient(colors)
            ThemeBrushOrientation.HORIZONTAL -> Brush.horizontalGradient(colors)
            ThemeBrushOrientation.VERTICAL -> Brush.verticalGradient(colors)
        }
    }
}

internal fun createFocusRingStyle(palette: ThemeColorPalette): NuvioFocusRingStyle {
    return NuvioFocusRingStyle(
        solidColor = palette.focusRing,
        gradientColors = palette.focusRingGradient
    )
}
