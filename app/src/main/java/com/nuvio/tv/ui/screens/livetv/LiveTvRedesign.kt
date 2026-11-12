package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/** Title block with the same accent bar the Settings panes use. */
@Composable
internal fun LiveTvHeader(title: String, subtitle: String, stats: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier.width(6.dp).height(64.dp).clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(NuvioTheme.colors.Secondary, NuvioTheme.colors.Secondary.copy(alpha = 0.3f))))
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.colors.TextSecondary, maxLines = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.forEach { LiveTvStatPill(it) }
        }
    }
}

@Composable
private fun LiveTvStatPill(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
    }
}

/** Mode/zone chip: the selected one is tinted, never "disabled", so it stays focusable. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTvChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.tv.material3.Button(
        onClick = onClick,
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = if (selected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.08f),
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(50)),
        modifier = Modifier.semantics { this.selected = selected },
    ) { Text(label, maxLines = 1) }
}

@Composable
internal fun LiveTvSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

/** Shown when there is nothing to watch yet: says what to do instead of an empty list. */
@Composable
internal fun LiveTvEmptyHero(title: String, body: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(NuvioTheme.colors.Secondary.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))))
            .padding(28.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.colors.TextSecondary)
        }
    }
}

/** One channel: the whole card plays it; the star toggles the favourite. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTvChannelCard(
    logo: @Composable () -> Unit,
    name: String,
    addonName: String?,
    details: @Composable () -> Unit,
    isFavorite: Boolean,
    favoriteLabel: String,
    opening: Boolean,
    openingLabel: String,
    onPlay: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = { onPlay?.invoke() },
            enabled = onPlay != null && !opening,
            modifier = Modifier.weight(1f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
            // Highlight without scaling: a scaled card spills outside the list's left edge.
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.06f),
                focusedContainerColor = Color.White.copy(alpha = 0.16f),
                disabledContainerColor = Color.White.copy(alpha = 0.04f),
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, NuvioTheme.focusRing.solidColor), shape = RoundedCornerShape(18.dp)),
            ),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.width(72.dp).height(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) { logo() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        addonName?.takeIf(String::isNotBlank)?.let { LiveTvAddonBadge(it) }
                    }
                    details()
                }
                if (opening) Text(openingLabel, style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextSecondary)
            }
        }
        androidx.tv.material3.Button(
            onClick = onToggleFavorite,
            colors = androidx.tv.material3.ButtonDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.06f),
                contentColor = if (isFavorite) NuvioTheme.colors.Secondary else NuvioTheme.colors.TextSecondary,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
            shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(50)),
            modifier = Modifier.semantics { contentDescription = favoriteLabel },
        ) { Text(if (isFavorite) "★" else "☆", style = MaterialTheme.typography.titleLarge) }
    }
}

/** Names the add-on a channel comes from, so the same channel from two add-ons can be told apart. */
@Composable
internal fun LiveTvAddonBadge(addonName: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(NuvioTheme.colors.Secondary.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            addonName,
            style = MaterialTheme.typography.labelMedium,
            color = NuvioTheme.colors.Secondary,
            maxLines = 1,
        )
    }
}
