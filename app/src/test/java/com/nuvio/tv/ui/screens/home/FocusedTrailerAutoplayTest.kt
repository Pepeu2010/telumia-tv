package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusedTrailerAutoplayTest {
    @Test fun continueWatchingUsesParentTitleForTrailerLookup() {
        val progress = WatchProgress("series-id", "series", "Nome da série", null, null, null,
            "episode-id", 1, 2, "Episódio", 500, 1000, 0)
        val selection = ModernPayload.ContinueWatching(ContinueWatchingItem.InProgress(progress, releaseInfo = "2024"))
        assertEquals(FocusedTrailerRequest("series-id", "Nome da série", "2024", "series"), selection.focusedTrailerRequest())
    }

    @Test fun heroPreviewWorksWithoutExpandingPortraitCards() {
        assertTrue(isFocusedTrailerAutoplayAvailable(true, FocusedPosterTrailerPlaybackTarget.HERO_MEDIA, false, false))
    }

    @Test fun cardPreviewRequiresAnExpandableCard() {
        assertFalse(isFocusedTrailerAutoplayAvailable(true, FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD, false, false))
        assertTrue(isFocusedTrailerAutoplayAvailable(true, FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD, false, true))
        assertTrue(isFocusedTrailerAutoplayAvailable(true, FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD, true, false))
    }

    @Test fun disabledPreferenceSuppressesBothTargets() {
        FocusedPosterTrailerPlaybackTarget.entries.forEach { target ->
            assertFalse(isFocusedTrailerAutoplayAvailable(false, target, true, true))
        }
    }
}
