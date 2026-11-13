package com.nuvio.tv.ui.screens.livetv

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clipToBounds
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvProgram
import com.nuvio.tv.ui.theme.NuvioTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ChannelColumnWidth = 188.dp
private val RailWidth = 232.dp
private val RowHeight = 58.dp
private val DpPerMinute = 5.4f
private const val WINDOW_MINUTES = 12 * 60
private const val SLOT_MINUTES = 30
private const val MILLIS_PER_MINUTE = 60_000L

private data class GuideCategory(val key: String, val label: String)

/**
 * TV guide: a preview of the focused programme on top, categories on the left and a time grid with a
 * "now" line. Categories are the add-ons the channels come from, so the same channel from two
 * add-ons is easy to tell apart.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveTvGuideView(
    channels: List<LiveTvChannel>,
    favoriteIds: Set<String>,
    programsByChannel: Map<String, List<LiveTvProgram>>,
    nowMillis: Long,
    zone: ZoneId,
    onPlay: (LiveTvChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember(zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }
    val windowStart = remember(nowMillis / (SLOT_MINUTES * MILLIS_PER_MINUTE)) {
        nowMillis - Math.floorMod(nowMillis, SLOT_MINUTES * MILLIS_PER_MINUTE)
    }
    val windowEnd = windowStart + WINDOW_MINUTES * MILLIS_PER_MINUTE

    val allLabel = stringResource(R.string.live_tv_guide_all_channels)
    val favoritesLabel = stringResource(R.string.live_tv_guide_favorites)
    val categories = remember(channels, favoriteIds, allLabel, favoritesLabel) {
        buildList {
            if (channels.any { it.id in favoriteIds }) add(GuideCategory("fav", favoritesLabel))
            add(GuideCategory("all", allLabel))
            channels.mapNotNull { it.addonDisplayName?.takeIf(String::isNotBlank) }.distinct()
                .forEach { add(GuideCategory("addon:$it", it)) }
        }
    }
    var categoryKey by remember { mutableStateOf("all") }
    val activeKey = categories.firstOrNull { it.key == categoryKey }?.key ?: "all"

    val rows = remember(channels, activeKey, favoriteIds, programsByChannel) {
        channels.filter { channel ->
            when {
                activeKey == "fav" -> channel.id in favoriteIds
                activeKey.startsWith("addon:") -> channel.addonDisplayName == activeKey.removePrefix("addon:")
                else -> true
            }
        }.sortedByDescending { !programsByChannel[it.id].isNullOrEmpty() }
    }

    var focusedChannel by remember { mutableStateOf<LiveTvChannel?>(null) }
    var focusedProgram by remember { mutableStateOf<LiveTvProgram?>(null) }
    var offsetPx by remember { mutableIntStateOf(0) }
    var viewportPx by remember { mutableIntStateOf(0) }
    val animatedOffset by animateIntAsState(offsetPx, tween(220), label = "guideOffset")
    val density = LocalDensity.current
    val pxPerMinute = with(density) { DpPerMinute.dp.toPx() }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(activeKey, rows.isNotEmpty()) {
        offsetPx = 0
        if (rows.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    val previewChannel = focusedChannel ?: rows.firstOrNull()
    val previewProgram = focusedProgram?.takeIf { focusedChannel != null }
        ?: previewChannel?.let { channel ->
            programsByChannel[channel.id].orEmpty().firstOrNull { nowMillis in it.startEpochMillis until it.endEpochMillis }
        }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GuidePreview(previewChannel, previewProgram, nowMillis, timeFormat)

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LazyColumn(Modifier.width(RailWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories, key = { it.key }) { category ->
                    val selected = category.key == activeKey
                    Surface(
                        onClick = { categoryKey = category.key },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) categoryKey = category.key },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = 0.18f),
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, NuvioTheme.focusRing.solidColor), shape = RoundedCornerShape(12.dp)),
                        ),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (selected) NuvioTheme.colors.Secondary else Color.Transparent)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                category.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                // Time header
                Row(Modifier.fillMaxWidth().height(30.dp)) {
                    Spacer(Modifier.width(ChannelColumnWidth + 8.dp))
                    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset { IntOffset(-animatedOffset, 0) }) {
                            for (slot in 0 until WINDOW_MINUTES / SLOT_MINUTES) {
                                Text(
                                    timeFormat.format(Instant.ofEpochMilli(windowStart + slot * SLOT_MINUTES * MILLIS_PER_MINUTE)),
                                    modifier = Modifier.width((SLOT_MINUTES * DpPerMinute).dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NuvioTheme.colors.TextSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(rows, key = { it.id }) { channel ->
                            val isFirst = channel.id == rows.first().id
                            GuideRow(
                                channel = channel,
                                programs = programsByChannel[channel.id].orEmpty(),
                                windowStart = windowStart,
                                windowEnd = windowEnd,
                                nowMillis = nowMillis,
                                offset = animatedOffset,
                                pxPerMinute = pxPerMinute,
                                timeFormat = timeFormat,
                                firstFocus = if (isFirst) firstFocus else null,
                                onViewport = { viewportPx = it },
                                onFocus = { program, startPx, endPx ->
                                    focusedChannel = channel
                                    focusedProgram = program
                                    offsetPx = when {
                                        endPx - startPx >= viewportPx || startPx < offsetPx -> startPx.coerceAtLeast(0)
                                        endPx > offsetPx + viewportPx -> endPx - viewportPx
                                        else -> offsetPx
                                    }
                                },
                                onPlay = { onPlay(channel) },
                            )
                        }
                    }
                    // "Now" line over the programme area.
                    Box(Modifier.padding(start = ChannelColumnWidth + 8.dp).fillMaxHeight().fillMaxWidth().clipToBounds()) {
                        val nowPx = ((nowMillis - windowStart).toFloat() / MILLIS_PER_MINUTE * pxPerMinute).toInt()
                        Box(
                            Modifier.offset { IntOffset(nowPx - animatedOffset, 0) }.width(2.dp).fillMaxHeight()
                                .background(NuvioTheme.colors.Secondary)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideRow(
    channel: LiveTvChannel,
    programs: List<LiveTvProgram>,
    windowStart: Long,
    windowEnd: Long,
    nowMillis: Long,
    offset: Int,
    pxPerMinute: Float,
    timeFormat: DateTimeFormatter,
    firstFocus: FocusRequester?,
    onViewport: (Int) -> Unit,
    onFocus: (LiveTvProgram?, Int, Int) -> Unit,
    onPlay: () -> Unit,
) {
    val segments = remember(programs, windowStart, windowEnd) {
        var cursor = windowStart
        programs.sortedBy(LiveTvProgram::startEpochMillis).mapNotNull { program ->
            val start = maxOf(program.startEpochMillis, cursor)
            val end = minOf(program.endEpochMillis, windowEnd)
            if (end <= start) return@mapNotNull null
            val gap = ((start - cursor) / MILLIS_PER_MINUTE).toInt()
            val minutes = ((end - start) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(1)
            cursor = end
            Triple(program, gap, minutes) to (start to end)
        }
    }
    val currentIndex = segments.indexOfFirst { (_, range) -> nowMillis in range.first until range.second }
    Row(Modifier.fillMaxWidth().height(RowHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onPlay,
            modifier = Modifier.width(ChannelColumnWidth).fillMaxHeight().onFocusChanged {
                if (it.isFocused) onFocus(segments.getOrNull(currentIndex.coerceAtLeast(0))?.first?.first, 0, 0)
            },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.07f),
                focusedContainerColor = Color.White.copy(alpha = 0.2f),
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, NuvioTheme.focusRing.solidColor), shape = RoundedCornerShape(12.dp)),
            ),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(40.dp).height(30.dp), contentAlignment = Alignment.Center) {
                    if (!channel.logoUrl.isNullOrBlank()) AsyncImage(
                        model = channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                    ) else Text(channel.displayName.take(2).uppercase(), style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextSecondary)
                }
                Text(channel.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds().onSizeChanged { onViewport(it.width) }) {
            Row(
                Modifier.wrapContentWidth(Alignment.Start, unbounded = true).offset { IntOffset(-offset, 0) },
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (segments.isEmpty()) {
                    GuideBlock(
                        title = stringResource(R.string.live_tv_guide_unavailable),
                        timeLabel = null,
                        widthMinutes = 180,
                        current = false,
                        progress = null,
                        requester = firstFocus,
                        onFocus = { onFocus(null, 0, 0) },
                        onClick = onPlay,
                    )
                } else {
                    segments.forEachIndexed { index, (info, range) ->
                        val (program, gap, minutes) = info
                        if (gap > 0) Spacer(Modifier.width((gap * DpPerMinute).dp))
                        val startPx = (((range.first - windowStart).toFloat() / MILLIS_PER_MINUTE) * pxPerMinute).toInt()
                        val endPx = (((range.second - windowStart).toFloat() / MILLIS_PER_MINUTE) * pxPerMinute).toInt()
                        val isCurrent = index == currentIndex
                        GuideBlock(
                            title = program.title,
                            timeLabel = "${timeFormat.format(Instant.ofEpochMilli(program.startEpochMillis))} – ${timeFormat.format(Instant.ofEpochMilli(program.endEpochMillis))}",
                            widthMinutes = minutes,
                            current = isCurrent,
                            progress = if (isCurrent) ((nowMillis - program.startEpochMillis).toFloat() /
                                (program.endEpochMillis - program.startEpochMillis).toFloat()).coerceIn(0f, 1f) else null,
                            requester = if (isCurrent || (currentIndex < 0 && index == 0)) firstFocus else null,
                            onFocus = { onFocus(program, startPx, endPx) },
                            onClick = onPlay,
                            accent = genreAccent(program.category),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideBlock(
    title: String,
    timeLabel: String?,
    widthMinutes: Int,
    current: Boolean,
    progress: Float?,
    requester: FocusRequester?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    accent: Color? = null,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width((widthMinutes * DpPerMinute).dp - 4.dp).fillMaxHeight().padding(end = 4.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (current) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.24f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, NuvioTheme.focusRing.solidColor), shape = RoundedCornerShape(10.dp)),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (accent != null) Box(Modifier.align(Alignment.CenterStart).width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.fillMaxSize().padding(start = if (accent != null) 16.dp else 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.Center) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (timeLabel != null && widthMinutes >= 40) {
                    Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
                }
            }
            if (progress != null) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(3.dp)
                        .background(NuvioTheme.colors.Secondary)
                )
            }
        }
    }
}

@Composable
private fun GuidePreview(
    channel: LiveTvChannel?,
    program: LiveTvProgram?,
    nowMillis: Long,
    timeFormat: DateTimeFormatter,
) {
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val subtitle = listOfNotNull(channel?.displayName, channel?.addonDisplayName?.takeIf(String::isNotBlank)).joinToString("  •  ")
            Text(subtitle, style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                program?.title ?: channel?.displayName.orEmpty(),
                style = scoreboard(46, 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (program != null) {
                val remaining = ((program.endEpochMillis - nowMillis) / MILLIS_PER_MINUTE).toInt()
                val live = nowMillis in program.startEpochMillis until program.endEpochMillis
                Text(
                    buildString {
                        append(timeFormat.format(Instant.ofEpochMilli(program.startEpochMillis)))
                        append(" – ")
                        append(timeFormat.format(Instant.ofEpochMilli(program.endEpochMillis)))
                        if (live && remaining >= 0) append("  •  ").append(stringResource(R.string.live_tv_guide_minutes_left, remaining))
                        program.category?.let { append("  •  ").append(it) }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                if (live) {
                    val progress = ((nowMillis - program.startEpochMillis).toFloat() /
                        (program.endEpochMillis - program.startEpochMillis).toFloat()).coerceIn(0f, 1f)
                    Box(Modifier.width(300.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(NuvioTheme.colors.Secondary))
                    }
                }
                program.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.colors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else if (channel != null) {
                Text(stringResource(R.string.live_tv_guide_unavailable_body), style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.colors.TextSecondary, maxLines = 2)
            }
        }
        Box(
            Modifier.height(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(NuvioTheme.colors.Secondary.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f)))),
            contentAlignment = Alignment.Center,
        ) {
            val image = program?.iconUrl?.takeIf(String::isNotBlank) ?: channel?.logoUrl?.takeIf(String::isNotBlank)
            if (image != null) AsyncImage(
                model = image, contentDescription = null,
                contentScale = if (program?.iconUrl.isNullOrBlank()) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (program?.iconUrl.isNullOrBlank()) Modifier.padding(28.dp) else Modifier),
            ) else Text(channel?.displayName?.take(2)?.uppercase().orEmpty(), style = MaterialTheme.typography.displaySmall, color = NuvioTheme.colors.TextSecondary)
        }
    }
}

/** A thin colour per genre so the grid can be scanned: news, sport, film, kids, series. */
private fun genreAccent(category: String?): Color? {
    val c = category?.lowercase() ?: return null
    return when {
        listOf("news", "notic", "jornal").any(c::contains) -> Color(0xFF4DA3FF)
        listOf("sport", "espor", "futebol", "soccer").any(c::contains) -> Color(0xFF3DDC97)
        listOf("movie", "film", "cine").any(c::contains) -> Color(0xFFB57BFF)
        listOf("kid", "child", "infan", "anim", "cartoon").any(c::contains) -> Color(0xFFFFD84D)
        listOf("series", "serie", "drama", "novela").any(c::contains) -> Color(0xFFFFA24D)
        else -> null
    }
}
