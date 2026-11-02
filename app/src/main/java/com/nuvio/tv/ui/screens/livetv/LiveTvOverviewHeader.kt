package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import com.nuvio.tv.LocalContentFocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LiveTvOverviewHeader(onManageSources: () -> Unit, onRefresh: () -> Unit, refreshEnabled: Boolean) {
    val contentFocus = LocalContentFocusRequester.current
    val colors = NuvioTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().padding(top = 72.dp).clip(shape)
        .background(Brush.horizontalGradient(listOf(colors.Panel, colors.Background)))
        .border(1.dp, colors.TextPrimary.copy(alpha = 0.10f), shape).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.LiveTv, null, tint = colors.Secondary, modifier = Modifier.size(34.dp))
            Text(stringResource(R.string.live_tv_title), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = colors.TextPrimary)
        }
        Text(stringResource(R.string.live_tv_description), style = MaterialTheme.typography.bodyMedium,
            color = colors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onManageSources, modifier = if (contentFocus == FocusRequester.Default) Modifier else Modifier.focusRequester(contentFocus)) { Text(stringResource(R.string.live_tv_sources)) }
            Button(onClick = onRefresh, enabled = refreshEnabled) { Text(stringResource(R.string.live_tv_refresh)) }
        }
    }
}
