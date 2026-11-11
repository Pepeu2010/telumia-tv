package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget

/** Hero previews don't need an expanded card; card previews do. */
internal fun isFocusedTrailerAutoplayAvailable(
    enabled: Boolean,
    target: FocusedPosterTrailerPlaybackTarget,
    landscapePosters: Boolean,
    expandEnabled: Boolean,
): Boolean = enabled && (
    target == FocusedPosterTrailerPlaybackTarget.HERO_MEDIA || landscapePosters || expandEnabled
)

internal data class FocusedTrailerRequest(val id: String, val title: String, val releaseInfo: String?, val type: String)

internal fun ModernPayload.focusedTrailerRequest(): FocusedTrailerRequest? = when (this) {
    is ModernPayload.Catalog -> FocusedTrailerRequest(itemId, trailerTitle, trailerReleaseInfo, trailerApiType)
    is ModernPayload.ContinueWatching -> when (val entry = item) {
        is ContinueWatchingItem.InProgress -> FocusedTrailerRequest(entry.progress.contentId, entry.progress.name, entry.releaseInfo, entry.progress.contentType)
        is ContinueWatchingItem.NextUp -> FocusedTrailerRequest(entry.info.contentId, entry.info.name, entry.info.releaseInfo, entry.info.contentType)
    }
    is ModernPayload.CollectionFolder -> null
}
