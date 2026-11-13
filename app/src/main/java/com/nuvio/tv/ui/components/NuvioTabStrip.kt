package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.BebasFamily
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Section tabs: text only, in the broadcast face, with a heavy accent underline on the selected one.
 * They deliberately do not look like the outlined pills used for filters, so the two levels of
 * navigation can be told apart at TV distance.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NuvioTabStrip(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Int = 23,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                onClick = { onSelect(index) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    contentColor = if (selected) Color.White else NuvioTheme.colors.TextSecondary,
                    focusedContentColor = Color(0xFF0B1014),
                ),
                border = ClickableSurfaceDefaults.border(border = Border.None),
                modifier = Modifier.semantics { this.selected = selected },
            ) {
                Column(Modifier.padding(horizontal = 11.dp).padding(top = 6.dp).width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        label.uppercase(),
                        fontFamily = BebasFamily,
                        fontSize = fontSize.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                    )
                    Box(
                        Modifier.padding(top = 2.dp).height(4.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) NuvioTheme.colors.Secondary else Color.Transparent)
                    )
                }
            }
        }
    }
}

/**
 * Compact segmented control on a solid dark plate, so it stays legible over any backdrop.
 * Used for quick filters that sit on top of artwork.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NuvioSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xF2080C10))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            Segment(label, index == selectedIndex) { onSelect(index) }
        }
        if (trailingLabel != null && onTrailing != null) {
            Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.18f)))
            Segment(trailingLabel, false, live = true, onClick = onTrailing)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Segment(label: String, selected: Boolean, live: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) NuvioTheme.colors.Secondary else Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = if (selected) Color(0xFF0B1014) else Color.White.copy(alpha = 0.86f),
            focusedContentColor = Color(0xFF0B1014),
        ),
        border = ClickableSurfaceDefaults.border(border = Border.None),
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF4D4F)))
                Box(Modifier.width(8.dp))
            }
            Text(
                label.uppercase(),
                fontFamily = BebasFamily,
                fontSize = 22.sp,
                letterSpacing = 1.2.sp,
                maxLines = 1,
            )
        }
    }
}
