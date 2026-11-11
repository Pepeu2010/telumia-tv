package com.nuvio.tv.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** A channel imported from a user-configured playlist; credentials stay only in its stream URL. */
@Serializable
data class LiveTvChannel(
    val id: String,
    val sourceId: String,
    val displayName: String,
    val streamUrl: String? = null,
    val logoUrl: String? = null,
    val groupName: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    /** Runtime-only addon routing data. Addon URLs can include private configuration tokens. */
    @Transient val addonBaseUrl: String? = null,
    @Transient val addonId: String? = null,
    @Transient val addonType: String? = null,
    @Transient val addonContentId: String? = null,
    @Transient val addonDisplayName: String? = null,
) {
    val isPlayable: Boolean
        get() = !streamUrl.isNullOrBlank() ||
            (!addonBaseUrl.isNullOrBlank() && !addonType.isNullOrBlank() && !addonContentId.isNullOrBlank())
}

/** Programme times are UTC epoch milliseconds so persisted guide data is timezone-independent. */
@Serializable
data class LiveTvProgram(
    val sourceId: String,
    val channelId: String,
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val description: String? = null,
    val category: String? = null,
    val iconUrl: String? = null,
)

@Serializable
data class LiveTvImportResult(
    val channels: List<LiveTvChannel>,
    val programs: List<LiveTvProgram> = emptyList(),
    val skippedEntries: Int = 0,
)
