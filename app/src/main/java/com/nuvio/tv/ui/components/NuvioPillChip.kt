package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * The app's tab/filter pill: filled accent when selected, outlined when not, white when focused.
 * Shared so Library, Live TV and every other tab row look like one system.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NuvioPillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) NuvioTheme.colors.Secondary else Color.Transparent,
            contentColor = if (selected) Color(0xFF0B1014) else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.14f)), shape = RoundedCornerShape(50)),
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        modifier = modifier.semantics { this.selected = selected },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
