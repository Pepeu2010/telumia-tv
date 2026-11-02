package com.nuvio.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.nuvio.tv.ui.theme.LocalUiMotion
import com.nuvio.tv.ui.theme.NuvioTheme

/** Uses the real drawer destinations, including TV; the remote can enter and leave each tab. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CinematicRootNavigationBar(
    items: List<DrawerItem>, selectedRoute: String?, onNavigate: (String) -> Unit,
    onExpandSidebar: () -> Unit, onEnterContent: () -> Boolean = { false }, modifier: Modifier = Modifier,
) {
    val colors = NuvioTheme.colors
    val mobile = LocalConfiguration.current.screenWidthDp < 600
    val shape = RoundedCornerShape(50)
    val motion = LocalUiMotion.current
    val menuLabel = stringResource(R.string.cd_expand_sidebar)
    Row(modifier.onPreviewKeyEvent { event ->
        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && onEnterContent()
    }.widthIn(max = 620.dp).fillMaxWidth(if (mobile) 0.96f else 0.80f)
        .testTag("cinematic-root-navigation")
        .clip(shape).background(colors.Background.copy(alpha = 0.88f))
        .border(1.dp, Color.White.copy(alpha = 0.18f), shape).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            val active = item.route == selectedRoute
            Button(onClick = { onNavigate(item.route) }, modifier = Modifier
                .weight(if (active && !mobile) 1.75f else 1f).height(40.dp)
                .semantics { selected = active; contentDescription = item.label },
                contentPadding = PaddingValues(horizontal = 8.dp),
                shape = ButtonDefaults.shape(shape),
                colors = ButtonDefaults.colors(
                    containerColor = if (active) colors.TextPrimary.copy(alpha = 0.12f) else Color.Transparent,
                    contentColor = colors.TextPrimary,
                    focusedContainerColor = colors.TextPrimary, focusedContentColor = colors.Background),
                scale = ButtonDefaults.scale(focusedScale = motion.scale(1.03f))) {
                DrawerItemIcon(item.iconRes, item.icon, Modifier.size(20.dp), tint = LocalContentColor.current)
                if (active && !mobile) {
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Button(onClick = onExpandSidebar, modifier = Modifier.size(40.dp)
            .semantics { contentDescription = menuLabel },
            contentPadding = PaddingValues(8.dp), shape = ButtonDefaults.shape(shape),
            colors = ButtonDefaults.colors(containerColor = Color.Transparent, contentColor = colors.TextPrimary,
                focusedContainerColor = colors.TextPrimary, focusedContentColor = colors.Background)) {
            Icon(Icons.Default.Menu, null, Modifier.size(20.dp))
        }
    }
}
