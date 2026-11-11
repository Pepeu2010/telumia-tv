@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun PlaybackFailureDetailsDialog(
    reportText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember(reportText) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 760.dp)
                .background(NuvioTheme.colors.BackgroundElevated, RoundedCornerShape(NuvioTheme.radii.xl))
                .padding(NuvioTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            Text(
                text = stringResource(R.string.playback_failure_details_title),
                style = MaterialTheme.typography.titleLarge,
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = stringResource(R.string.playback_failure_details_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
            Text(
                text = reportText,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(NuvioTheme.radii.md))
                    .padding(NuvioTheme.spacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Playback issue", reportText))
                    copied = true
                }) {
                    Text(stringResource(if (copied) R.string.playback_failure_details_copied else R.string.playback_failure_details_copy))
                }
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}
