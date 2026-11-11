package com.nuvio.tv.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailerVideoPreferenceTest {
    private val extractor = InAppYouTubeExtractor()
    private fun video(height: Int) = StreamCandidate("android", 1, "fixture-$height", height.toDouble(), false, "1", height, 30, "mp4")

    @Test fun prefersKnown720pOrLessWhenAvailable() {
        assertEquals(listOf(720, 480), extractor.preferTvTrailerVideoCandidates(listOf(video(2160), video(720), video(480), video(0))).map { it.height })
    }

    @Test fun retainsOriginalChoicesWhenNoBoundedStreamExists() {
        val candidates = listOf(video(1080), video(0))
        assertEquals(candidates, extractor.preferTvTrailerVideoCandidates(candidates))
    }
}
