package com.nuvio.tv.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioStrokes
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.navigation.Screen
import kotlinx.coroutines.delay
import java.util.Date

@Immutable
data class TopNavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopNavigationBar(
    items: List<TopNavigationItem>,
    selectedRoute: String?,
    activeProfileName: String,
    activeProfileColorHex: String,
    activeProfileAvatarImageUrl: String?,
    showProfileSelector: Boolean,
    onSwitchProfile: () -> Unit,
    onNavigate: (String) -> Unit,
    onMoveToContent: () -> Unit,
    onNavigationItemFocused: (String) -> Unit,
    selectedItemFocusRequester: FocusRequester,
    showClock: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(context, showClock) {
        if (!showClock) return@LaunchedEffect
        val formatter = DateFormat.getTimeFormat(context)
        while (true) {
            clockText = formatter.format(Date())
            delay(30_000L)
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        val compactLayout = maxWidth < 800.dp
        // Keep the route bar icon-led at every TV width. Destination names stay
        // available to accessibility services through each card's semantics.
        val iconOnlyItemWidth = when {
            maxWidth < 360.dp -> 40.dp
            compactLayout -> 52.dp
            else -> 58.dp
        }
        val horizontalInset = when {
            compactLayout -> 8.dp
            maxWidth >= 1_200.dp -> 56.dp
            maxWidth >= 950.dp -> 48.dp
            else -> 16.dp
        }
        val itemSpacing = if (compactLayout) 2.dp else 4.dp
        val profileWidth = if (activeProfileName.isNotBlank()) {
            if (compactLayout) iconOnlyItemWidth + 4.dp else 44.dp
        } else {
            0.dp
        }
        val contentWidth = (maxWidth - horizontalInset * 2).coerceAtLeast(0.dp)
        val requestedItemWidths = List(items.size) { iconOnlyItemWidth }
        val requestedItemsWidth = requestedItemWidths.fold(0.dp) { total, width -> total + width }
        val requestedNavigationWidth = requestedItemsWidth + itemSpacing * (items.size - 1) + 24.dp
        val profileClearance = if (profileWidth > 0.dp) profileWidth + 16.dp else 0.dp
        val availableNavigationWidth = (contentWidth - profileClearance * 2).coerceAtLeast(0.dp)
        val navigationWidth = requestedNavigationWidth.coerceAtMost(availableNavigationWidth)
        val availableItemsWidth = (navigationWidth - 24.dp - itemSpacing * (items.size - 1)).coerceAtLeast(0.dp)
        val itemWidthScale = if (requestedItemsWidth > 0.dp) {
            minOf(1f, availableItemsWidth / requestedItemsWidth)
        } else {
            1f
        }
        val navigationItemWidths = requestedItemWidths.map { it * itemWidthScale }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.78f),
                            Color.Black.copy(alpha = 0.50f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalInset)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(navigationWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
                var navigationHasFocus by remember { mutableStateOf(false) }
                var focusedRoute by remember { mutableStateOf<String?>(null) }
                val selectedIndicatorOffset by animateDpAsState(
                    targetValue = 12.dp + navigationItemWidths.take(selectedIndex).fold(0.dp) { total, width -> total + width } +
                        itemSpacing * selectedIndex,
                    animationSpec = if (com.nuvio.tv.ui.theme.LocalUiMotion.current.allowsSpatialEffects) {
                        androidx.compose.animation.core.spring(
                            dampingRatio = 0.78f,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    } else {
                        tween(durationMillis = 0)
                    },
                    label = "topNavigationSelectionOffset"
                )
                Box(
                    modifier = Modifier
                        .width(navigationWidth)
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xDA070707))
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.06f))),
                            RoundedCornerShape(50)
                        )
                        .onFocusChanged { navigationHasFocus = it.hasFocus }
                        .focusGroup(),
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = selectedIndicatorOffset)
                            .padding(vertical = 5.dp)
                            .width(navigationItemWidths[selectedIndex])
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            // Accent-tinted glass pill that glides between routes.
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        NuvioTheme.colors.Secondary.copy(alpha = 0.34f),
                                        NuvioTheme.colors.Secondary.copy(alpha = 0.14f)
                                    )
                                )
                            )
                            .border(1.dp, NuvioTheme.colors.Secondary.copy(alpha = 0.55f), RoundedCornerShape(50))
                    )
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEachIndexed { index, item ->
                            TopNavigationButton(
                                item = item,
                                selected = item.route == selectedRoute,
                                selectedItemFocusRequester = selectedItemFocusRequester.takeIf { item.route == selectedRoute },
                                onClick = { onNavigate(item.route) },
                                onMoveToContent = onMoveToContent,
                                onNavigationItemFocused = { route ->
                                    focusedRoute = route
                                    onNavigationItemFocused(route)
                                },
                                modifier = Modifier.width(navigationItemWidths[index])
                            )
                        }
                    }
                }
                // Icons alone are hard to learn; name the focused destination under the capsule.
                val labelRoute = focusedRoute ?: selectedRoute
                val focusedLabel = items.firstOrNull { it.route == labelRoute }?.label.orEmpty()
                if (navigationHasFocus && focusedLabel.isNotBlank()) {
                    Text(
                        text = focusedLabel,
                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = 22.dp),
                        color = NuvioTheme.colors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }

            if (activeProfileName.isNotBlank()) {
                if (showProfileSelector) {
                    TopNavigationProfileButton(
                        name = activeProfileName,
                        colorHex = activeProfileColorHex,
                        avatarImageUrl = activeProfileAvatarImageUrl,
                        showName = false,
                        onClick = onSwitchProfile,
                        onMoveToContent = onMoveToContent,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(profileWidth)
                    )
                } else {
                    ProfileAvatarCircle(
                        name = activeProfileName,
                        colorHex = activeProfileColorHex,
                        size = 32.dp,
                        avatarImageUrl = activeProfileAvatarImageUrl,
                        imageCrossfade = false,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
            }

            if (showClock && clockText.isNotBlank()) {
                Text(
                    text = clockText,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    color = NuvioTheme.colors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopNavigationButton(
    item: TopNavigationItem,
    selected: Boolean,
    selectedItemFocusRequester: FocusRequester?,
    onClick: () -> Unit,
    onMoveToContent: () -> Unit,
    onNavigationItemFocused: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    var isFocused by remember { mutableStateOf(false) }
    val shape = NuvioTheme.shapes.navItem
    val motion = NuvioTheme.motion
    val containerColor by animateColorAsState(
        targetValue = if (isFocused) NuvioTheme.colors.FocusBackground else Color.Transparent,
        animationSpec = tween(motion.durations.fast),
        label = "topNavigationItemBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) NuvioTheme.colors.FocusRing else Color.Transparent,
        animationSpec = tween(motion.durations.fast),
        label = "topNavigationItemFocusBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            isFocused -> NuvioTheme.colors.TextPrimary
            selected -> NuvioTheme.colors.Secondary
            else -> NuvioTheme.colors.TextSecondary
        },
        animationSpec = tween(motion.durations.fast),
        label = "topNavigationItemIconTint",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = tween(motion.durations.fast),
        label = "topNavigationItemIconScale",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .then(selectedItemFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .fillMaxHeight()
            .padding(vertical = 5.dp)
            .onFocusChanged {
                isFocused = it.hasFocus
                if (it.isFocused) onNavigationItemFocused(item.route)
            }
            .focusProperties {
                up = FocusRequester.Cancel
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    if (!focusManager.moveFocus(FocusDirection.Down)) onMoveToContent()
                    true
                } else {
                    false
                }
            }
            .semantics {
                this.selected = selected
                contentDescription = item.label
            },
        colors = CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor
        ),
        border = CardDefaults.border(
            border = androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border(
                border = BorderStroke(NuvioStrokes.tokens.focus, borderColor),
                shape = shape
            )
        ),
        shape = CardDefaults.shape(shape = shape),
        scale = CardDefaults.scale(
            focusedScale = motion.focusScale,
            pressedScale = motion.pressedScale
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp).graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopNavigationProfileButton(
    name: String,
    colorHex: String,
    avatarImageUrl: String?,
    showName: Boolean,
    onClick: () -> Unit,
    onMoveToContent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    val shape = NuvioTheme.shapes.navItem
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) NuvioTheme.colors.FocusRing else Color.Transparent,
        animationSpec = tween(NuvioTheme.motion.durations.fast),
        label = "topNavigationProfileFocusBorder"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) NuvioTheme.colors.FocusBackground else Color.Transparent,
        animationSpec = tween(NuvioTheme.motion.durations.fast),
        label = "topNavigationProfileBackground"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .semantics { contentDescription = name }
            .onFocusChanged { isFocused = it.hasFocus }
            .focusProperties { up = FocusRequester.Cancel }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    if (!focusManager.moveFocus(FocusDirection.Down)) onMoveToContent()
                    true
                } else {
                    false
                }
            },
        colors = CardDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = backgroundColor
        ),
        border = CardDefaults.border(
            border = androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border(
                border = BorderStroke(NuvioStrokes.tokens.focus, borderColor),
                shape = shape
            )
        ),
        shape = CardDefaults.shape(shape = shape),
        scale = CardDefaults.scale(
            focusedScale = NuvioTheme.motion.focusScale,
            pressedScale = NuvioTheme.motion.pressedScale
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (showName) 8.dp else 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatarCircle(
                name = name,
                colorHex = colorHex,
                size = if (showName) 30.dp else 28.dp,
                avatarImageUrl = avatarImageUrl,
                imageCrossfade = false
            )
            if (showName) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isFocused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp)
                )
            }
        }
    }
}
