package com.nuvio.tv.updater.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.updater.UpdateUiState
import com.nuvio.tv.updater.model.AppUpdate

@Composable
internal fun UpdateDownloadProgressDialog(
    state: UpdateUiState,
    update: AppUpdate
) {
    val transition = rememberInfiniteTransition(label = "updateDownloadProgress")
    val indeterminateProgress by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "indeterminateUpdateDownloadProgress"
    )
    val progress = (state.downloadProgress ?: indeterminateProgress).coerceIn(0f, 1f)
    val displayedProgress = (progress * 100).toInt().coerceIn(0, 100)

    NuvioDialog(
        onDismiss = {},
        title = stringResource(R.string.update_downloading),
        subtitle = update.title,
        width = 560.dp,
        suppressFirstKeyUp = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(NuvioTheme.colors.BackgroundCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(NuvioTheme.colors.Primary, CircleShape)
                )
            }

            Text(
                text = if (state.downloadProgress != null) {
                    stringResource(R.string.update_downloading_progress, displayedProgress)
                } else {
                    stringResource(R.string.update_preparing_download)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
        }
    }
}
