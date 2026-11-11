package com.nuvio.tv.data.repository

internal data class ParsedContentIds(
    val imdb: String? = null,
    val tmdb: Int? = null,
    val simkl: Long? = null
)

internal fun parseContentIds(contentId: String?): ParsedContentIds {
    if (contentId.isNullOrBlank()) return ParsedContentIds()
    val raw = contentId.trim()

    if (raw.startsWith("tt")) {
        return ParsedContentIds(imdb = raw.substringBefore(':'))
    }

    if (raw.startsWith("tmdb:", ignoreCase = true)) {
        return ParsedContentIds(tmdb = raw.substringAfter(':').toIntOrNull())
    }

    if (raw.startsWith("simkl:", ignoreCase = true)) {
        return ParsedContentIds(simkl = raw.substringAfter(':').toLongOrNull())
    }

    return ParsedContentIds()
}
