package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.LocalUiMotion

/** Informational only: previews must never steal focus from the selected title. */
@Composable
internal fun HomePreviewPlaybackBadge(visible: Boolean, muted: Boolean, modifier: Modifier = Modifier) {
    val motion = LocalUiMotion.current
    AnimatedVisibility(visible = visible, modifier = modifier,
        enter = fadeIn(tween(motion.durationMillis(180))),
        exit = fadeOut(tween(motion.durationMillis(120)))) {
        val shape = RoundedCornerShape(percent = 50)
        Row(
            modifier = Modifier.testTag("home-preview-playing")
                .background(Color.Black.copy(alpha = 0.72f), shape)
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
            Text(stringResource(if (muted) R.string.home_preview_muted else R.string.home_preview_with_sound),
                color = Color.White, fontSize = 12.sp, maxLines = 1)
        }
    }
}
