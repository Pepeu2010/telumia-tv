package com.nuvio.tv.ui.screens.livetv

import com.nuvio.tv.domain.model.LiveTvChannel

internal fun liveTvPlayableChannels(channels: List<LiveTvChannel>): List<LiveTvChannel> =
    channels.filter { !it.streamUrl.isNullOrBlank() }

internal fun liveTvQuickChannelStartIndex(
    channels: List<LiveTvChannel>,
    currentChannelId: String?,
): Int {
    if (channels.isEmpty()) return -1
    return channels.indexOfFirst { it.id == currentChannelId }.takeIf { it >= 0 } ?: 0
}
