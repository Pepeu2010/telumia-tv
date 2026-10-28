package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvProgram
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun LiveTvQuickChannelOverlay(
    channels: List<LiveTvChannel>,
    programs: List<LiveTvProgram>,
    favoriteChannelIds: Set<String>,
    currentChannelId: String?,
    timeZoneId: String,
    onSelectChannel: (LiveTvChannel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return

    val currentIndex = remember(channels, currentChannelId) {
        liveTvQuickChannelStartIndex(channels, currentChannelId)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    val selectedFocusRequester = remember { FocusRequester() }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val zone = remember(timeZoneId) {
        runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
    }
    val timeFormatter = remember(zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }
    val programsByChannel = remember(programs) {
        programs.groupBy { it.sourceId to it.channelId }
            .mapValues { (_, channelPrograms) -> channelPrograms.sortedBy(LiveTvProgram::startEpochMillis) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex)
            delay(100L)
            runCatching { selectedFocusRequester.requestFocus() }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.48f)
                .padding(start = 36.dp, top = 28.dp, bottom = 28.dp)
                .background(NuvioTheme.colors.Background.copy(alpha = 0.96f), RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.live_tv_quick_switch_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTheme.colors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.live_tv_channels, channels.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary,
                    )
                }
                Button(onClick = onClose) { Text(stringResource(R.string.live_tv_quick_switch_close)) }
            }
            Text(
                text = stringResource(R.string.live_tv_quick_switch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary,
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    val currentProgram = channel.tvgId?.takeIf(String::isNotBlank)
                        ?.let { programsByChannel[channel.sourceId to it] }
                        ?.firstOrNull { nowMillis >= it.startEpochMillis && nowMillis < it.endEpochMillis }
                    val nextProgram = channel.tvgId?.takeIf(String::isNotBlank)
                        ?.let { programsByChannel[channel.sourceId to it] }
                        ?.firstOrNull { it.startEpochMillis > nowMillis }
                    val isCurrentChannel = channel.id == currentChannelId

                    Button(
                        onClick = { onSelectChannel(channel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == currentIndex) Modifier.focusRequester(selectedFocusRequester) else Modifier),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = channel.displayName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (channel.id in favoriteChannelIds) {
                                    Text("★", color = NuvioTheme.colors.SecondaryVariant)
                                }
                                if (isCurrentChannel) {
                                    Text(
                                        stringResource(R.string.live_tv_quick_switch_current),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NuvioTheme.colors.SecondaryVariant,
                                    )
                                }
                            }
                            currentProgram?.let {
                                Text(
                                    text = stringResource(
                                        R.string.live_tv_quick_switch_now,
                                        timeFormatter.format(java.time.Instant.ofEpochMilli(it.endEpochMillis)),
                                        it.title,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            nextProgram?.let {
                                Text(
                                    text = stringResource(
                                        R.string.live_tv_quick_switch_next,
                                        timeFormatter.format(java.time.Instant.ofEpochMilli(it.startEpochMillis)),
                                        it.title,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
