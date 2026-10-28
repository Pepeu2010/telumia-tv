package com.nuvio.tv.ui.screens.livetv

import com.nuvio.tv.domain.model.LiveTvChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvQuickChannelPolicyTest {
    private val channels = listOf(
        LiveTvChannel(id = "guide-only", sourceId = "source", displayName = "Guide only"),
        LiveTvChannel(id = "one", sourceId = "source", displayName = "One", streamUrl = "https://example.test/one.m3u8"),
        LiveTvChannel(id = "two", sourceId = "source", displayName = "Two", streamUrl = "https://example.test/two.m3u8"),
        LiveTvChannel(id = "blank", sourceId = "source", displayName = "Blank", streamUrl = "  "),
    )

    @Test
    fun quickSelectorOnlyOffersChannelsWithAStreamUrl() {
        assertEquals(listOf("one", "two"), liveTvPlayableChannels(channels).map(LiveTvChannel::id))
    }

    @Test
    fun quickSelectorStartsOnTheCurrentChannel() {
        val playable = liveTvPlayableChannels(channels)

        assertEquals(1, liveTvQuickChannelStartIndex(playable, "two"))
    }

    @Test
    fun quickSelectorFallsBackToFirstPlayableChannelWhenCurrentIsUnavailable() {
        val playable = liveTvPlayableChannels(channels)

        assertEquals(0, liveTvQuickChannelStartIndex(playable, "removed"))
    }

    @Test
    fun quickSelectorReportsAnEmptyListWithoutInventingAChannel() {
        assertTrue(liveTvPlayableChannels(emptyList()).isEmpty())
        assertEquals(-1, liveTvQuickChannelStartIndex(emptyList(), null))
    }
}
