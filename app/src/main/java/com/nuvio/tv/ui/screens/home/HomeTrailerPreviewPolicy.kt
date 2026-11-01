package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget

/** Hero playback is independent of whether portrait cards expand. Touch layouts remain static. */
internal fun shouldEnableFocusedTrailerPreview(
    focusEffectsEnabled: Boolean,
    preferenceEnabled: Boolean,
    playbackTarget: FocusedPosterTrailerPlaybackTarget,
    landscapePosters: Boolean,
    expandedCards: Boolean,
): Boolean = focusEffectsEnabled && preferenceEnabled && (
    playbackTarget == FocusedPosterTrailerPlaybackTarget.HERO_MEDIA || landscapePosters || expandedCards
)
