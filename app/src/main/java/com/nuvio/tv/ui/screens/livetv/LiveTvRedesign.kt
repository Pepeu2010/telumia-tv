package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

/** Condensed scoreboard face used for titles, clock times and scores across the TV tab. */
internal val ScoreboardFont = com.nuvio.tv.ui.theme.BebasFamily

internal fun scoreboard(size: Int, spacing: Float = 0.5f) =
    TextStyle(fontFamily = ScoreboardFont, fontSize = size.sp, letterSpacing = spacing.sp, fontWeight = FontWeight.Normal)

internal val LiveRed = Color(0xFFFF4D4F)
internal val OnAirGreen = Color(0xFF3DDC97)
private val Ink = Color(0xFF0B1014)

/**
 * Header: title, the mode tabs, and plain status text at the far end. Tabs are text-only
 * (see NuvioTabStrip) and the status is not a pill, so nothing here competes with the filters below.
 */
@Composable
internal fun LiveTvTopBar(title: String, stats: List<String>, modes: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.width(5.dp).height(34.dp).clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(NuvioTheme.colors.Secondary, NuvioTheme.colors.Secondary.copy(alpha = 0.25f))))
            )
            Text(title.uppercase(), style = scoreboard(36, 1.5f), color = NuvioTheme.colors.TextPrimary, maxLines = 1)
        }
        Row(Modifier.weight(1f), content = modes)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            stats.forEach { LiveTvStat(it) }
        }
    }
}

@Composable
private fun LiveTvStat(text: String) {
    val live = text.startsWith("\u25CF")
    Text(
        text.removePrefix("\u25CF").trim(),
        style = MaterialTheme.typography.labelLarge,
        color = if (live) LiveRed else NuvioTheme.colors.TextSecondary,
        maxLines = 1,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** Mode/zone chip: the shared pill; the selected one is filled, never "disabled", so it stays focusable. */
@Composable
internal fun LiveTvChip(label: String, selected: Boolean, onClick: () -> Unit) =
    com.nuvio.tv.ui.components.NuvioPillChip(label, selected, onClick)

@Composable
internal fun LiveTvSectionTitle(text: String) {
    Text(text.uppercase(), style = scoreboard(26, 1f), modifier = Modifier.padding(top = 8.dp))
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
            Text(title.uppercase(), style = scoreboard(36, 1f))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.colors.TextSecondary)
        }
    }
}

/**
 * One channel: the whole card plays it; the star toggles the favourite. When the guide has art for
 * the programme on air, it fades in from the right edge, so the list looks like a broadcast schedule.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTvChannelCard(
    logo: @Composable () -> Unit,
    name: String,
    addonName: String?,
    art: String?,
    onAir: Boolean,
    details: @Composable () -> Unit,
    isFavorite: Boolean,
    favoriteLabel: String,
    opening: Boolean,
    openingLabel: String,
    onPlay: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = { onPlay?.invoke() },
            enabled = onPlay != null && !opening,
            modifier = Modifier.weight(1f),
            shape = ClickableSurfaceDefaults.shape(shape),
            // Highlight without scaling: a scaled card spills outside the list's left edge.
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF131B21),
                focusedContainerColor = Color(0xFF1B262E),
                disabledContainerColor = Color(0xFF10171C),
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), shape = shape),
                focusedBorder = Border(BorderStroke(3.dp, NuvioTheme.focusRing.solidColor), shape = shape),
            ),
        ) {
            Box(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                // On-air marker: a green edge along the whole card, visible from across the room.
                if (onAir) Box(
                    Modifier.align(Alignment.CenterStart).width(5.dp).fillMaxHeight().background(OnAirGreen)
                )
                if (!art.isNullOrBlank()) {
                    Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(360.dp)) {
                        AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(listOf(Color(0xFF131B21), Color(0xFF131B21).copy(alpha = 0.55f), Color.Transparent))
                            )
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(
                        Modifier.width(120.dp).height(72.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)))),
                        contentAlignment = Alignment.Center,
                    ) { logo() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (onAir) Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(50)).background(OnAirGreen))
                            Text(
                                name.uppercase(),
                                style = scoreboard(34, 1.2f),
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
            .background(NuvioTheme.colors.Secondary.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Text(
            addonName,
            style = MaterialTheme.typography.labelMedium,
            color = NuvioTheme.colors.Secondary,
            maxLines = 1,
        )
    }
}

/** Kept for callers that still render the tall title block. */
@Composable
internal fun LiveTvHeader(title: String, subtitle: String, stats: List<String>) {
    LiveTvTopBar(title, stats) { }
}

/** Pill-shaped search field that matches the chips instead of the stock Material outline. */
@Composable
internal fun LiveTvSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(60)) },
        modifier = modifier,
        singleLine = true,
        placeholder = { androidx.compose.material3.Text(placeholder, color = NuvioTheme.colors.TextSecondary) },
        shape = RoundedCornerShape(50),
        textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NuvioTheme.colors.Secondary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = NuvioTheme.colors.Secondary,
            focusedContainerColor = Color.White.copy(alpha = 0.06f),
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}
