package com.nuvio.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Telumia's launch "tudum": a thin line of light opens into a warm halo on the first hit of the
 * sound, the wordmark lands on the second, and two beams sweep across it as the shimmer rises.
 * Everything is a pure function of [progress] (0..1 over [TUDUM_DURATION_MILLIS]).
 */
const val TUDUM_DURATION_MILLIS = 2400

private val Warm = Color(0xFFF4A455)
private val Coral = Color(0xFFFF7A5C)
private val Hot = Color(0xFFFFD9A8)

internal fun tudumSegment(progress: Float, from: Float, to: Float): Float =
    ((progress - from) / (to - from)).coerceIn(0f, 1f)

private fun ease(x: Float): Float = FastOutSlowInEasing.transform(x)

/** Wordmark opacity: arrives with the second hit. */
internal fun tudumBrandAlpha(progress: Float): Float = ease(tudumSegment(progress, 0.17f, 0.42f))

/** Wordmark scale: lands from slightly large, with a tiny impact pulse on each hit. */
internal fun tudumBrandScale(progress: Float): Float {
    val land = 1.10f - 0.10f * ease(tudumSegment(progress, 0.17f, 0.55f))
    val hit1 = (tudumSegment(progress, 0.04f, 0.075f) - tudumSegment(progress, 0.075f, 0.16f)) * 0.012f
    val hit2 = (tudumSegment(progress, 0.17f, 0.195f) - tudumSegment(progress, 0.195f, 0.30f)) * 0.022f
    return land + hit1 + hit2
}

/** The loader only fades in once the intro has landed. */
internal fun tudumLoaderAlpha(progress: Float): Float = tudumSegment(progress, 0.78f, 0.95f)

/** Draws the line, halo and beams. [centerYFraction] is where the wordmark sits on screen. */
internal fun Modifier.tudumLight(progress: () -> Float, centerYFraction: Float = 0.426f): Modifier = drawBehind {
    val p = progress()
    val cx = size.width / 2f
    val cy = size.height * centerYFraction

    // 1. A line of light stretches out from the centre.
    val line = ease(tudumSegment(p, 0.02f, 0.18f))
    val lineAlpha = (1f - tudumSegment(p, 0.20f, 0.36f)) * minOf(1f, line * 3f)
    if (lineAlpha > 0.01f) {
        val half = size.width * 0.32f * line
        val thickness = 3.dp.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Warm, Hot, Warm, Color.Transparent),
                startX = cx - half,
                endX = cx + half,
            ),
            topLeft = Offset(cx - half, cy - thickness / 2f),
            size = Size(half * 2f, thickness),
            alpha = lineAlpha,
        )
    }

    // 2. The line blooms into a warm elliptical halo that settles to a soft glow.
    val bloom = ease(tudumSegment(p, 0.07f, 0.34f))
    val settle = 0.52f + 0.48f * (1f - tudumSegment(p, 0.34f, 0.75f))
    if (bloom > 0.01f) {
        scale(scaleX = 1f, scaleY = 0.5f, pivot = Offset(cx, cy)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Warm.copy(alpha = 0.60f), Coral.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.width * 0.34f * bloom + 1f,
                ),
                radius = size.width * 0.34f * bloom + 1f,
                center = Offset(cx, cy),
                alpha = bloom * settle,
            )
        }
    }

    // 3. Two slanted beams sweep across while the shimmer rises.
    for ((index, offset) in listOf(0f, 0.07f).withIndex()) {
        val s = tudumSegment(p, 0.36f + offset, 0.80f + offset)
        if (s <= 0f || s >= 1f) continue
        val x = size.width * (-0.25f + 1.5f * ease(s))
        val bell = sin(s * PI.toFloat())
        val width = size.width * (if (index == 0) 0.075f else 0.035f)
        rotate(degrees = 16f, pivot = Offset(x, cy)) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Hot.copy(alpha = 0.55f), Color.White.copy(alpha = 0.85f), Hot.copy(alpha = 0.55f), Color.Transparent),
                    startX = x - width,
                    endX = x + width,
                ),
                topLeft = Offset(x - width, cy - size.height),
                size = Size(width * 2f, size.height * 2f),
                alpha = bell * (if (index == 0) 0.55f else 0.35f),
            )
        }
    }
}
