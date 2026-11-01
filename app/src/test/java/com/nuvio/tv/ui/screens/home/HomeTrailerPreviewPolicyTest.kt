package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTrailerPreviewPolicyTest {
    @Test fun portraitHeroDoesNotDependOnExpandingCards() {
        assertTrue(shouldEnableFocusedTrailerPreview(true, true,
            FocusedPosterTrailerPlaybackTarget.HERO_MEDIA, false, false))
    }

    @Test fun optedOutProfilesAndTouchLayoutsDoNotStartFocusedPreviews() {
        assertFalse(shouldEnableFocusedTrailerPreview(true, false,
            FocusedPosterTrailerPlaybackTarget.HERO_MEDIA, true, true))
        assertFalse(shouldEnableFocusedTrailerPreview(false, true,
            FocusedPosterTrailerPlaybackTarget.HERO_MEDIA, true, true))
    }

    @Test fun expandedTargetRequiresAnExpandedOrLandscapeCard() {
        assertFalse(shouldEnableFocusedTrailerPreview(true, true,
            FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD, false, false))
        assertTrue(shouldEnableFocusedTrailerPreview(true, true,
            FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD, false, true))
        assertTrue(shouldEnableFocusedTrailerPreview(true, true,
            FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD, true, false))
    }
}
