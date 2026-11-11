package com.nuvio.tv.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.LocalUiMotion
import com.nuvio.tv.ui.util.contentLayoutDirection

private val MarqueeVelocity = 45.dp
internal const val MarqueeIterations = 3

internal data class FocusMarqueePresentation(
    val animate: Boolean,
    val wrapLines: Boolean,
)

internal fun focusMarqueePresentation(
    focused: Boolean,
    allowsSpatialEffects: Boolean,
    wrapWhenMotionDisabled: Boolean,
): FocusMarqueePresentation = FocusMarqueePresentation(
    animate = focused && allowsSpatialEffects,
    wrapLines = wrapWhenMotionDisabled && !allowsSpatialEffects,
)

/**
 * Text that scrolls (marquees) horizontally while [focused] if the content overflows and motion
 * allows spatial effects. Otherwise it ellipsizes, unless [wrapWhenMotionDisabled] requests a
 * static multi-line layout for reduced or disabled motion.
 *
 * The layout direction is derived from the text's own content (via [contentLayoutDirection]), so RTL titles
 * marquee correctly even when the app's ambient layout direction is LTR, and vice versa.
 *
 * @param wrapWhenMotionDisabled keeps labels complete and readable without animation when motion is
 * reduced or disabled. The caller's parent must allow the resulting height to grow.
 */
@Composable
fun FocusMarqueeText(
    text: String,
    focused: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    wrapWhenMotionDisabled: Boolean = false,
) {
    val motion = LocalUiMotion.current
    val presentation = focusMarqueePresentation(
        focused = focused,
        allowsSpatialEffects = motion.allowsSpatialEffects,
        wrapWhenMotionDisabled = wrapWhenMotionDisabled,
    )
    val currentDirection = LocalLayoutDirection.current
    val textDirection = remember(text) { text.contentLayoutDirection() }
    val needsDirectionOverride = textDirection != currentDirection

    val textModifier = if (presentation.animate) {
        modifier.basicMarquee(iterations = MarqueeIterations, velocity = MarqueeVelocity)
    } else {
        modifier
    }
    val textOverflow = if (presentation.animate || presentation.wrapLines) {
        TextOverflow.Clip
    } else {
        TextOverflow.Ellipsis
    }

    val content = @Composable {
        Text(
            text = text,
            modifier = textModifier,
            style = style,
            color = color,
            maxLines = if (presentation.wrapLines) Int.MAX_VALUE else 1,
            softWrap = presentation.wrapLines,
            overflow = textOverflow,
            textAlign = textAlign,
        )
    }

    if (needsDirectionOverride) {
        CompositionLocalProvider(LocalLayoutDirection provides textDirection) {
            content()
        }
    } else {
        content()
    }
}
