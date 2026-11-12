package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.data.local.LiveTvChannelStateStore
import kotlinx.coroutines.delay
import androidx.tv.material3.Button
import androidx.compose.ui.semantics.selected
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvProgram
import com.nuvio.tv.ui.components.LiveTvChannelTile
import com.nuvio.tv.ui.components.LiveTvGuideRow
import com.nuvio.tv.ui.components.LiveTvNowNext
import com.nuvio.tv.ui.components.LiveTvProgramPresentation
import com.nuvio.tv.ui.theme.NuvioTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LiveTvScreen(
    onPlayChannel: (LiveTvChannel) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    val selectedZone = remember(state.timeZoneId) {
        if (state.timeZoneId == LiveTvChannelStateStore.DEFAULT_TIME_ZONE_ID) ZoneId.systemDefault()
        else runCatching { ZoneId.of(state.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
    }
    val formatter = remember(selectedZone) { DateTimeFormatter.ofPattern("HH:mm").withZone(selectedZone) }
    val sourceDateFormatter = remember(selectedZone) { DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(selectedZone) }
    val programsByChannel = remember(state.programs) {
        state.programs.groupBy { it.sourceId to it.channelId }
            .mapValues { (_, programs) -> programs.sortedBy(LiveTvProgram::startEpochMillis) }
    }
    val allChannels = remember(state.channels, state.addonChannels) {
        (state.channels + state.addonChannels).distinctBy(LiveTvChannel::id)
    }
    var selectedMode by remember { mutableStateOf(LiveTvMode.NOW) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(selectedMode) {
        if (selectedMode == LiveTvMode.GUIDE) return@LaunchedEffect
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    LaunchedEffect(state.lastSavedSourceId) {
        if (state.lastSavedSourceId != null) {
            name = ""
            playlistUrl = ""
            epgUrl = ""
        }
    }

    // Add-on channels get their schedule from epgshare01 (keyed by channel id); M3U channels from their own XMLTV.
    val channelPrograms = remember(allChannels, state.guidePrograms, programsByChannel) {
        allChannels.associate { channel ->
            channel.id to (state.guidePrograms[channel.id]
                ?: channel.tvgId?.takeIf(String::isNotBlank)?.let { programsByChannel[channel.sourceId to it] }.orEmpty())
        }
    }
    val hasGuide = channelPrograms.values.any { it.isNotEmpty() }
    val channelsById = remember(allChannels) { allChannels.associateBy(LiveTvChannel::id) }
    LaunchedEffect(hasGuide) {
        if (!hasGuide && selectedMode == LiveTvMode.GUIDE) selectedMode = LiveTvMode.NOW
    }
    val visibleChannels = remember(allChannels, state.favoriteChannelIds, state.recentChannelIds, selectedMode) {
        when (selectedMode) {
            LiveTvMode.FAVORITES -> allChannels.filter { it.id in state.favoriteChannelIds }
            LiveTvMode.RECENTS -> state.recentChannelIds.mapNotNull(channelsById::get)
            else -> allChannels
        }
    }
    fun playChannel(channel: LiveTvChannel) {
        viewModel.playChannel(channel, onPlayChannel)
    }

    val nothingToWatch = allChannels.isEmpty() && !state.isLoading && !state.isLoadingAddonChannels

    if (selectedMode == LiveTvMode.GUIDE && hasGuide) {
        Column(
            modifier = Modifier.fillMaxSize().background(NuvioTheme.colors.Background).padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                items(LiveTvMode.values().filter { it != LiveTvMode.GUIDE || hasGuide }, key = { it.name }) { mode ->
                    LiveTvChip(stringResource(mode.label), selectedMode == mode) {
                        if (mode == LiveTvMode.GUIDE) nowMillis = System.currentTimeMillis()
                        selectedMode = mode
                    }
                }
            }
            LiveTvGuideView(
                channels = allChannels,
                favoriteIds = state.favoriteChannelIds,
                programsByChannel = channelPrograms,
                nowMillis = nowMillis,
                zone = selectedZone,
                onPlay = ::playChannel,
                modifier = Modifier.weight(1f).padding(bottom = 16.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(NuvioTheme.colors.Background).padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LiveTvHeader(
                title = stringResource(R.string.live_tv_title),
                subtitle = stringResource(R.string.live_tv_description_addons),
                stats = buildList {
                    if (allChannels.isNotEmpty()) add(stringResource(R.string.live_tv_channels, allChannels.size))
                    if (state.isLoadingGuide) add(stringResource(R.string.live_tv_guide_loading))
                    else if (state.guideTotalChannels > 0) add(stringResource(R.string.live_tv_guide_coverage, state.guideMatchedChannels, state.guideTotalChannels))
                },
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                items(LiveTvMode.values().filter { it != LiveTvMode.GUIDE || hasGuide }, key = { it.name }) { mode ->
                    LiveTvChip(stringResource(mode.label), selectedMode == mode) {
                        if (mode == LiveTvMode.GUIDE) nowMillis = System.currentTimeMillis()
                        selectedMode = mode
                    }
                }
            }
        }
        if (state.isLoading) item { Text(stringResource(R.string.live_tv_loading), color = NuvioTheme.colors.TextSecondary) }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (state.backgroundRefreshUnavailable) {
            item { Text(stringResource(R.string.live_tv_refresh_schedule_unavailable), color = NuvioTheme.colors.TextSecondary) }
        }

        if (selectedMode == LiveTvMode.SOURCES) {
            item { LiveTvSectionTitle(stringResource(R.string.live_tv_guide_regions_title)) }
            item {
                Text(
                    stringResource(R.string.live_tv_guide_regions_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(com.nuvio.tv.core.livetv.EpgShareRepository.REGIONS, key = { it.code }) { region ->
                        LiveTvChip(region.label, region.code in state.epgRegions) { viewModel.toggleEpgRegion(region.code) }
                    }
                }
            }
            if (state.guideFailedRegions.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.live_tv_guide_failed, state.guideFailedRegions.joinToString()),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.live_tv_m3u_advanced_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary,
                )
            }
            item {
                Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.live_tv_add_source), style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(value = name, onValueChange = { name = it.take(80) },
                            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.live_tv_source_name)) }, singleLine = true)
                        OutlinedTextField(value = playlistUrl, onValueChange = { playlistUrl = it.take(4096) },
                            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.live_tv_playlist_url)) }, singleLine = true)
                        OutlinedTextField(value = epgUrl, onValueChange = { epgUrl = it.take(4096) },
                            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.live_tv_epg_url)) }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                viewModel.addSource(name, playlistUrl, epgUrl.ifBlank { null })
                            }, enabled = !state.isLoading && playlistUrl.isNotBlank()) {
                                Text(stringResource(R.string.live_tv_save_source))
                            }
                            Button(onClick = viewModel::refresh, enabled = !state.isLoading && state.sources.any { it.enabled }) {
                                Text(stringResource(R.string.live_tv_refresh))
                            }
                        }
                    }
                }
            }
            if (state.sources.isNotEmpty()) {
                item { LiveTvSectionTitle(stringResource(R.string.live_tv_timezone)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(LIVE_TV_TIME_ZONES, key = { it.id }) { option ->
                            LiveTvChip(stringResource(option.label), state.timeZoneId == option.id) { viewModel.setTimeZone(option.id) }
                        }
                    }
                }
                item { LiveTvSectionTitle(stringResource(R.string.live_tv_sources)) }
                items(state.sources, key = { "source:${it.id}" }) { source ->
                    Surface(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name, style = MaterialTheme.typography.titleMedium)
                                val sourceStatus = state.sourceErrors[source.id]
                                    ?: state.lastUpdatedEpochMillis[source.id]?.let { timestamp ->
                                        stringResource(R.string.live_tv_updated, sourceDateFormatter.format(java.time.Instant.ofEpochMilli(timestamp)))
                                    }
                                    ?: source.playlistUrl.hostForDisplay()
                                Text(sourceStatus,
                                    style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
                            }
                            Button(onClick = { viewModel.setSourceEnabled(source, !source.enabled) }) {
                                Text(if (source.enabled) stringResource(R.string.live_tv_disable) else stringResource(R.string.live_tv_enable))
                            }
                            Button(onClick = { viewModel.removeSource(source) }) { Text(stringResource(R.string.live_tv_remove)) }
                        }
                    }
                }
            }
        } else {
            if (nothingToWatch) {
                item {
                    LiveTvEmptyHero(
                        stringResource(R.string.live_tv_empty_hero_title),
                        stringResource(R.string.live_tv_empty_hero_body),
                    )
                }
            }
        if (state.isLoadingAddonChannels) {
            item { Text(stringResource(R.string.live_tv_addon_channels_loading), color = NuvioTheme.colors.TextSecondary) }
        } else if (state.addonCatalogCount == 0) {
            item { Text(stringResource(R.string.live_tv_addon_catalogs_empty), color = NuvioTheme.colors.TextSecondary) }
        } else if (state.addonChannels.isEmpty()) {
            item { Text(stringResource(R.string.live_tv_addon_channels_empty), color = NuvioTheme.colors.TextSecondary) }
        }
        if (state.addonCatalogErrorCount > 0) {
            item {
                Text(
                    stringResource(R.string.live_tv_addon_catalog_errors, state.addonCatalogErrorCount),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

            if (!state.isLoading && !state.isLoadingAddonChannels && visibleChannels.isEmpty() && !nothingToWatch) {
                val emptyMessage = when {
                    allChannels.isEmpty() -> R.string.live_tv_empty_channels
                    selectedMode == LiveTvMode.FAVORITES -> R.string.live_tv_empty_favorites
                    selectedMode == LiveTvMode.RECENTS -> R.string.live_tv_empty_recents
                    else -> R.string.live_tv_empty_channels
                }
                item { Text(stringResource(emptyMessage), color = NuvioTheme.colors.TextSecondary) }
            }
        if (selectedMode == LiveTvMode.GUIDE && hasGuide) {
            val windowStart = nowMillis.floorToHour()
            val windowEnd = windowStart + GUIDE_WINDOW_MILLIS
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(NuvioTheme.components.liveTv.channelWidth + 18.dp))
                    Row(Modifier.weight(1f)) {
                        (0..2).forEach { hour ->
                            val instant = java.time.Instant.ofEpochMilli(windowStart + hour * HOUR_MILLIS)
                            Text(formatter.format(instant), modifier = Modifier.width(NuvioTheme.components.liveTv.programWidth),
                                style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextSecondary)
                        }
                    }
                }
            }
            items(visibleChannels, key = { "guide-channel:${it.id}" }) { channel ->
                val tvgId = channel.tvgId?.takeIf(String::isNotBlank)
                val sourcePrograms = tvgId?.let { programsByChannel[channel.sourceId to it] }.orEmpty()
                val segments = tvgId?.let { guideSegments(sourcePrograms, channel.sourceId, it, windowStart, windowEnd) }.orEmpty()
                val rowPrograms = segments.map { segment ->
                    LiveTvProgramPresentation(
                        key = "${channel.id}:${segment.program.startEpochMillis}:${segment.program.title}",
                        title = segment.program.title,
                        timeLabel = "${formatter.format(java.time.Instant.ofEpochMilli(segment.startMillis))} – ${formatter.format(java.time.Instant.ofEpochMilli(segment.endMillis))}",
                        isCurrent = nowMillis >= segment.program.startEpochMillis && nowMillis < segment.program.endEpochMillis,
                        gapBeforeMinutes = segment.gapBeforeMinutes,
                        durationMinutes = segment.durationMinutes,
                    )
                }
                LiveTvGuideRow(
                    channelName = channel.displayName,
                    channelNumber = null,
                    logoUrl = channel.logoUrl,
                    selectedChannel = false,
                    programs = rowPrograms,
                    onChannelClick = if (channel.isPlayable) ({ playChannel(channel) }) else null,
                    onProgramClick = { if (channel.isPlayable) playChannel(channel) },
                    modifier = Modifier.fillMaxWidth(),
                    scrollState = rememberLazyListState(),
                    isFavorite = channel.id in state.favoriteChannelIds,
                    onFavoriteClick = { viewModel.toggleFavorite(channel.id) },
                    favoriteLabel = stringResource(R.string.live_tv_add_favorite),
                    unfavoriteLabel = stringResource(R.string.live_tv_remove_favorite),
                )
            }
        } else items(visibleChannels, key = { "channel:${it.id}" }) { channel ->
            val programs = channelPrograms[channel.id].orEmpty()
            val now = nowMillis
            val current = programs.firstOrNull { now >= it.startEpochMillis && now < it.endEpochMillis }
            val next = programs.firstOrNull { it.startEpochMillis > now }
            val progress = current?.let {
                ((now - it.startEpochMillis).toFloat() / (it.endEpochMillis - it.startEpochMillis).toFloat()).coerceIn(0f, 1f)
            }
            val resolving = channel.id in state.resolvingChannelIds
            LiveTvChannelCard(
                logo = {
                    if (!channel.logoUrl.isNullOrBlank()) coil3.compose.AsyncImage(
                        model = channel.logoUrl, contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    ) else Text(channel.displayName.take(2).uppercase(), style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextSecondary)
                },
                name = channel.displayName,
                addonName = channel.addonDisplayName,
                details = {
                    if (selectedMode == LiveTvMode.CHANNELS) {
                        if (!channel.isPlayable) Text(stringResource(R.string.live_tv_no_current_program),
                            style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary)
                    } else if (programs.isNotEmpty()) {
                        val nowLabel = current?.let { "${formatter.format(java.time.Instant.ofEpochMilli(it.startEpochMillis))}  ${it.title}" }
                            ?: stringResource(R.string.live_tv_no_current_program)
                        val nextLabel = next?.let { "${formatter.format(java.time.Instant.ofEpochMilli(it.startEpochMillis))}  ${it.title}" }
                        LiveTvNowNext(nowLabel, nextLabel, stringResource(R.string.live_tv_next), progress)
                    }
                },
                isFavorite = channel.id in state.favoriteChannelIds,
                favoriteLabel = stringResource(if (channel.id in state.favoriteChannelIds) R.string.live_tv_remove_favorite else R.string.live_tv_add_favorite),
                opening = resolving,
                openingLabel = stringResource(R.string.live_tv_opening_channel),
                onPlay = if (channel.isPlayable) ({ playChannel(channel) }) else null,
                onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
            )
        }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 28.dp)) }
    }
}

private data class LiveTvTimeZoneOption(val id: String, val label: Int)

private val LIVE_TV_TIME_ZONES = listOf(
    LiveTvTimeZoneOption(LiveTvChannelStateStore.DEFAULT_TIME_ZONE_ID, R.string.live_tv_timezone_device),
    LiveTvTimeZoneOption("America/Sao_Paulo", R.string.live_tv_timezone_brasilia),
    LiveTvTimeZoneOption("America/Manaus", R.string.live_tv_timezone_manaus),
    LiveTvTimeZoneOption("America/Rio_Branco", R.string.live_tv_timezone_rio_branco),
    LiveTvTimeZoneOption("America/Noronha", R.string.live_tv_timezone_noronha),
    LiveTvTimeZoneOption("UTC", R.string.live_tv_timezone_utc),
)

private enum class LiveTvMode(val label: Int) {
    NOW(R.string.live_tv_now_view),
    GUIDE(R.string.live_tv_guide_view),
    CHANNELS(R.string.live_tv_channels_view),
    FAVORITES(R.string.live_tv_favorites_view),
    RECENTS(R.string.live_tv_recents_view),
    SOURCES(R.string.live_tv_sources_view),
}

@Composable
private fun FavoriteAction(isFavorite: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (isFavorite) R.string.live_tv_remove_favorite else R.string.live_tv_add_favorite)
    Button(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(if (isFavorite) "★" else "☆")
    }
}

private fun Long.floorToHour(): Long = this - Math.floorMod(this, HOUR_MILLIS)

private const val HOUR_MILLIS = 60L * 60L * 1000L
private const val GUIDE_WINDOW_MILLIS = 3L * HOUR_MILLIS

private fun String.hostForDisplay(): String = runCatching { java.net.URI(this).host.orEmpty() }
    .getOrDefault("").ifBlank { stringResourceFallback }

private const val stringResourceFallback = "URL configurada"

