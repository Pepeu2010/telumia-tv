package com.nuvio.tv.ui.screens.player

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.nuvio.tv.data.local.toTrackPreference

internal fun PlayerRuntimeController.preparePlaybackBeforeStart(
    url: String,
    headers: Map<String, String>,
    loadSavedProgress: Boolean
) {
    _uiState.update { it.copy(embeddedChapterMetadata = null) }
    val playbackRequest = PlayerMediaSourceFactory.normalizePlaybackRequest(url, headers)
    val playbackUrl = playbackRequest.url
    val playbackHeaders = playbackRequest.headers
    if (playbackUrl != currentStreamUrl || playbackHeaders != currentHeaders) {
        currentStreamUrl = playbackUrl
        currentHeaders = playbackHeaders
        _uiState.update { it.copy(currentStreamUrl = playbackUrl) }
    }

    logSwitchTrace(
        stage = "prepare-playback-before-start",
        message = "urlHash=${playbackUrl.hashCode().toUInt().toString(16)} loadSavedProgress=$loadSavedProgress " +
            "clearPendingSwitchPref=true"
    )
    val clickElapsedMs = launchStartedAtElapsedMs
        ?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }
        ?: -1L
    queuePlaybackRawEventLine(
        "PREPARE_PLAYBACK: clickElapsedMs=$clickElapsedMs host=${playbackUrl.safeScrobbleHost()} " +
            "loadSavedProgress=$loadSavedProgress currentSeason=${currentSeason ?: -1} " +
            "currentEpisode=${currentEpisode ?: -1} streamName=${_uiState.value.currentStreamName ?: "n/a"}"
    )
    clearPendingEngineSwitchTrackPreference()
    playbackPreparationJob?.cancel()

    playbackPreparationJob = scope.launch {
        setLoadingStatus(
            phase = "preparing_metadata",
            message = context.getString(com.nuvio.tv.R.string.player_loading_preparing)
        )
        refreshScrobbleItem()
        if (persistedTrackPreference == null) {
            contentId?.let { id ->
                val loaded = trackPreferenceDataStore.load(id)?.toTrackPreference()
                logSwitchTrace(
                    stage = "track-pref-load",
                    message = "contentId=$id loadedAudio=${loaded?.audio?.language}/${loaded?.audio?.name} " +
                        "loadedSubtitle=${loaded?.subtitle?.javaClass?.simpleName ?: "none"}"
                )
                Log.d(
                    PlayerRuntimeController.TAG,
                    "TRACK_PREF load: contentId=$id S${currentSeason}E${currentEpisode} " +
                        "result=${if (loaded == null) "null (no saved preference)" else "audio=${loaded.audio?.language}/${loaded.audio?.name} subtitle=${loaded.subtitle?.javaClass?.simpleName}"}"
                )
                persistedTrackPreference = loaded
            } ?: Log.d(PlayerRuntimeController.TAG, "TRACK_PREF load: skipped (contentId is null)")
            // Subtitle delay is keyed per-videoId, so it is loaded separately
            // from the track selection above. This happens before
            // initializePlayer() because the renderers factory snapshots
            // subtitleDelayUs from _uiState.value.subtitleDelayMs on build.
            currentVideoId?.takeIf { it.isNotBlank() }?.let { vid ->
                val savedDelayMs = trackPreferenceDataStore.loadSubtitleDelayMs(vid)
                if (savedDelayMs != null && savedDelayMs != 0) {
                    subtitleDelayUs.set(savedDelayMs.toLong() * 1000L)
                    _uiState.update { it.copy(subtitleDelayMs = savedDelayMs) }
                    Log.d(
                        PlayerRuntimeController.TAG,
                        "TRACK_PREF load: restored subtitleDelayMs=$savedDelayMs for videoId=$vid"
                    )
                }
            }
        } else {
            Log.d(
                PlayerRuntimeController.TAG,
                "TRACK_PREF load: skipped (persistedTrackPreference already set: " +
                    "audio=${persistedTrackPreference?.audio?.language}/${persistedTrackPreference?.audio?.name} " +
                    "subtitle=${persistedTrackPreference?.subtitle?.javaClass?.simpleName})"
            )
            logSwitchTrace(
                stage = "track-pref-load",
                message = "skipped=true reason=persisted-already-set " +
                    "audio=${persistedTrackPreference?.audio?.language}/${persistedTrackPreference?.audio?.name} " +
                    "subtitle=${persistedTrackPreference?.subtitle?.javaClass?.simpleName ?: "none"}"
            )
        }
        contentId?.takeIf { it.isNotBlank() }?.let { id ->
            trackPreferenceDataStore.loadPlaybackSpeed(id)?.let { speed ->
                _uiState.update { it.copy(playbackSpeed = speed) }
            }
        }
        // Load saved watch progress BEFORE player init.
        // This eliminates the race condition where ExoPlayer's STATE_READY
        // callback fired before the DB read completed, causing the resume
        // seek to be silently skipped — the player would start from 0:00
        // or hang in buffering after a late seek.
        if (loadSavedProgress) {
            recordLoadingDiagnosticEvent(
                phase = "loading_saved_progress",
                message = context.getString(com.nuvio.tv.R.string.player_loading_preparing)
            )
            loadSavedProgressSuspend(currentSeason, currentEpisode)
        }
        recordLoadingDiagnosticEvent(
            phase = "initializing_player",
            message = context.getString(com.nuvio.tv.R.string.player_loading_building)
        )
        initializePlayer(playbackUrl, playbackHeaders)
    }
}

private fun String.safeScrobbleHost(): String {
    return runCatching {
        android.net.Uri.parse(this).host ?: substringBefore("://").takeIf { it.isNotBlank() } ?: "unknown"
    }.getOrDefault("unknown")
}
