package com.nuvio.tv.ui.screens.player

import java.net.URI
import java.util.Locale

enum class PlaybackFailureSource(val label: String) {
    STREAM("Stream"),
    TRAILER("Trailer")
}

enum class PlaybackFailureStage(val label: String) {
    SOURCE_SELECTION("Source selection"),
    STARTUP("Startup"),
    PLAYBACK("Playback")
}

/** A small, local-only report. It deliberately contains no content, account, URL, or header data. */
data class PlaybackFailureReport(
    val source: PlaybackFailureSource,
    val stage: PlaybackFailureStage,
    val format: String,
    val firstFrame: String,
    val errorCode: String,
    val summary: String
) {
    fun asText(): String = buildString {
        appendLine("Playback issue")
        appendLine("Source: ${source.label}")
        appendLine("Stage: ${stage.label}")
        appendLine("Format: $format")
        appendLine("First frame: $firstFrame")
        appendLine("Error code: $errorCode")
        append("Error: $summary")
    }

    companion object {
        fun create(
            source: PlaybackFailureSource,
            stage: PlaybackFailureStage,
            mimeType: String? = null,
            mediaUrl: String? = null,
            firstFrameRendered: Boolean? = null,
            errorCode: String? = null,
            errorMessage: String? = null
        ): PlaybackFailureReport = PlaybackFailureReport(
            source = source,
            stage = stage,
            format = safeMediaFormat(mimeType, mediaUrl),
            firstFrame = when (firstFrameRendered) {
                true -> "rendered"
                false -> "not rendered"
                null -> "not available"
            },
            errorCode = safeErrorCode(errorCode),
            summary = sanitizePlaybackError(errorMessage)
        )
    }
}

private fun safeMediaFormat(mimeType: String?, mediaUrl: String?): String {
    val normalizedMime = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
    when (normalizedMime) {
        "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl" -> return "HLS"
        "application/dash+xml" -> return "DASH"
        "video/mp4", "audio/mp4" -> return "MP4"
        "video/webm", "audio/webm" -> return "WebM"
        "video/x-matroska" -> return "Matroska"
        "video/mp2t" -> return "MPEG-TS"
    }

    val path = runCatching { URI(mediaUrl).rawPath.orEmpty() }.getOrDefault("")
    return when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "m3u8" -> "HLS"
        "mpd" -> "DASH"
        "mp4", "m4v" -> "MP4"
        "webm" -> "WebM"
        "mkv" -> "Matroska"
        "ts" -> "MPEG-TS"
        else -> "Unknown"
    }
}

private fun safeErrorCode(value: String?): String {
    val code = value.orEmpty().trim()
    return code.takeIf { ERROR_CODE_REGEX.matches(it) } ?: "Unknown"
}

/**
 * Keep useful plain-language error text while removing common secret-bearing forms.
 * The report builder never receives URLs or request headers as fields.
 */
internal fun sanitizePlaybackError(value: String?): String {
    var result = value.orEmpty().trim()
    if (result.isEmpty()) return "No additional error detail."

    result = URL_REGEX.replace(result, "[URL removed]")
    result = BARE_URL_REGEX.replace(result, "[URL removed]")
    result = USER_INFO_REGEX.replace(result, "[credentials removed]@")
    result = COOKIE_REGEX.replace(result) { "${it.groupValues[1]}=[redacted]" }
    result = SENSITIVE_VALUE_REGEX.replace(result) { "${it.groupValues[1]}=[redacted]" }
    result = BEARER_REGEX.replace(result, "Bearer [redacted]")
    result = EMAIL_REGEX.replace(result, "[account removed]")
    result = JWT_REGEX.replace(result, "[token removed]")
    result = LONG_SECRET_REGEX.replace(result, "[value removed]")
    result = QUERY_REGEX.replace(result, "[query removed]")
    result = IPV4_REGEX.replace(result, "[host removed]")
    result = ACCOUNT_LABEL_REGEX.replace(result, "[account removed]")
    result = WINDOWS_PATH_REGEX.replace(result, "[path removed]")
    result = UNIX_PATH_REGEX.replace(result, "[path removed]")
    result = result.replace(WHITESPACE_REGEX, " ").trim().take(MAX_SUMMARY_LENGTH)
    return result.ifBlank { "No additional error detail." }
}

private const val MAX_SUMMARY_LENGTH = 240
private val ERROR_CODE_REGEX = Regex("[A-Z][A-Z0-9_]{1,79}")
private val URL_REGEX = Regex("(?i)\\b(?:https?|rtsp|file|content|ftp)://[^\\s\\\"'<>]+")
private val BARE_URL_REGEX = Regex("(?i)\\b(?:www\\.)?(?:[a-z0-9-]+\\.)+[a-z]{2,}(?::[0-9]{1,5})?(?:/[^\\s\\\"'<>]*)?")
private val USER_INFO_REGEX = Regex("(?i)\\b[^/@\\s:]+:[^/@\\s]+@")
private val COOKIE_REGEX = Regex("(?im)\\b(cookies?|set-cookie)\\s*[:=]\\s*[^\\r\\n}]*")
private val SENSITIVE_VALUE_REGEX = Regex(
    "(?i)\\b(authorization|proxy-authorization|api[_-]?key|access[_-]?key|access[_-]?token|id[_-]?token|refresh[_-]?token|token|password|passwd|secret|session|signature|sig|x-amz-(?:credential|signature|security-token)|x-goog-(?:credential|signature)|cloudfront-(?:key-pair-id|policy|signature)|key-pair-id|policy|account(?:[-_ ]?id)?|user(?:name|[-_ ]?id)?|email)\\s*[:=]\\s*(?:bearer\\s+)?(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}]+)"
)
private val BEARER_REGEX = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/-]+=*")
private val EMAIL_REGEX = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
private val JWT_REGEX = Regex("\\b[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b")
private val LONG_SECRET_REGEX = Regex("\\b[A-Za-z0-9_-]{32,}\\b")
private val QUERY_REGEX = Regex("\\?[^\\s\\\"'<>]*")
private val IPV4_REGEX = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?\\b")
private val ACCOUNT_LABEL_REGEX = Regex("(?i)\\b(account|user|profile)(?:[ _-]?(?:id|name))?\\s+(?:is\\s+)?[a-z0-9._@-]{2,}")
private val WINDOWS_PATH_REGEX = Regex("(?i)\\b[A-Z]:\\\\[^\\s\\\"'<>]+")
private val UNIX_PATH_REGEX = Regex("(?<!:)\\B/(?:[^\\s/]+/)+[^\\s/]*")
private val WHITESPACE_REGEX = Regex("\\s+")
