@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.LocalUiMotion

@Composable
internal fun SceneInfoDialog(
    entries: List<TvSceneInfoEntry>,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    val motion = LocalUiMotion.current
    val actionScale = ButtonDefaults.scale(focusedScale = motion.scale(1.03f), pressedScale = motion.scale(0.98f))
    LaunchedEffect(Unit) { closeFocus.requestFocusAfterFrames() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .align(Alignment.Center)
                    .background(NuvioTheme.colors.BackgroundElevated, RoundedCornerShape(24.dp))
                    .padding(28.dp)
                    .testTag("scene-info-panel"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.scene_info_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = NuvioTheme.colors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onDismiss,
                        scale = actionScale,
                        modifier = Modifier.focusRequester(closeFocus).testTag("scene-info-close"),
                    ) { Text(stringResource(R.string.scene_info_close)) }
                }
                Text(
                    text = stringResource(R.string.scene_info_embedded_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                entries.take(16).forEachIndexed { index, entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(14.dp))
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .testTag("scene-info-entry-$index"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.scene_info_chapter),
                            style = MaterialTheme.typography.labelMedium,
                            color = NuvioTheme.colors.TextSecondary,
                        )
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = NuvioTheme.colors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatSceneInfoTime(entry.startMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

private fun formatSceneInfoTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
