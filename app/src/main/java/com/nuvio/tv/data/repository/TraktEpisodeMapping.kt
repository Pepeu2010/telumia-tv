package com.nuvio.tv.data.repository

import com.nuvio.tv.core.storage.BoundedLruCache

internal data class EpisodeMappingEntry(
    val season: Int,
    val episode: Int,
    val title: String? = null,
    val videoId: String? = null
)

internal fun remapEpisodeByTitleOrIndex(
    requestedSeason: Int,
    requestedEpisode: Int,
    requestedVideoId: String?,
    requestedTitle: String? = null,
    addonEpisodes: List<EpisodeMappingEntry>,
    traktEpisodes: List<EpisodeMappingEntry>,
    titleNormalizer: EpisodeTitleNormalizer,
): EpisodeMappingEntry? {
    return remapEpisodeBetweenLists(
        requestedSeason = requestedSeason,
        requestedEpisode = requestedEpisode,
        requestedVideoId = requestedVideoId,
        requestedTitle = requestedTitle,
        sourceEpisodes = addonEpisodes,
        targetEpisodes = traktEpisodes,
        titleNormalizer = titleNormalizer,
    )
}

internal fun reverseRemapEpisodeByTitleOrIndex(
    requestedSeason: Int,
    requestedEpisode: Int,
    requestedVideoId: String? = null,
    requestedTitle: String? = null,
    addonEpisodes: List<EpisodeMappingEntry>,
    traktEpisodes: List<EpisodeMappingEntry>,
    titleNormalizer: EpisodeTitleNormalizer,
): EpisodeMappingEntry? {
    return remapEpisodeBetweenLists(
        requestedSeason = requestedSeason,
        requestedEpisode = requestedEpisode,
        requestedVideoId = requestedVideoId,
        requestedTitle = requestedTitle,
        sourceEpisodes = traktEpisodes,
        targetEpisodes = addonEpisodes,
        titleNormalizer = titleNormalizer,
    )
}

private fun remapEpisodeBetweenLists(
    requestedSeason: Int,
    requestedEpisode: Int,
    requestedVideoId: String?,
    requestedTitle: String?,
    sourceEpisodes: List<EpisodeMappingEntry>,
    targetEpisodes: List<EpisodeMappingEntry>,
    titleNormalizer: EpisodeTitleNormalizer,
): EpisodeMappingEntry? {
    if (sourceEpisodes.isEmpty() || targetEpisodes.isEmpty()) return null

    val orderedSourceEpisodes = sourceEpisodes
        .sortedWith(compareBy(EpisodeMappingEntry::season, EpisodeMappingEntry::episode))
    val orderedTargetEpisodes = targetEpisodes
        .sortedWith(compareBy(EpisodeMappingEntry::season, EpisodeMappingEntry::episode))

    val currentSourceEpisode = requestedVideoId
        ?.takeIf { it.isNotBlank() }
        ?.let { videoId ->
            orderedSourceEpisodes.firstOrNull { it.videoId == videoId }
        }
        ?: orderedSourceEpisodes.firstOrNull {
            it.season == requestedSeason && it.episode == requestedEpisode
        }
        ?: return null

    // The service-owned tier-limited cache reuses recent normalized titles across
    // remaps without retaining every addon/provider title for the process lifetime.
    val normalizedTitle = titleNormalizer.normalize(requestedTitle ?: currentSourceEpisode.title)
    if (isUsefulEpisodeTitle(normalizedTitle)) {
        val titleMatches = orderedTargetEpisodes.filter {
            titleNormalizer.normalize(it.title) == normalizedTitle
        }
        if (titleMatches.size == 1) {
            return titleMatches.first()
        }
    }

    val sourceIndex = orderedSourceEpisodes.indexOf(currentSourceEpisode)
    if (sourceIndex !in orderedTargetEpisodes.indices) return null

    return orderedTargetEpisodes[sourceIndex]
}

private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private val COLLAPSED_SPACES = Regex("\\s+")

internal class EpisodeTitleNormalizer(capacity: Int) {
    private val normalizedTitles = BoundedLruCache<String, String>(capacity)

    fun normalize(title: String?): String {
        if (title == null) return ""
        normalizedTitles[title]?.let { return it }
        return normalizeEpisodeTitle(title).also { normalizedTitles[title] = it }
    }

    val size: Int
        get() = normalizedTitles.size
}

private fun normalizeEpisodeTitle(title: String): String = title.lowercase()
    .replace(NON_ALPHANUMERIC, " ")
    .trim()
    .replace(COLLAPSED_SPACES, " ")

private fun isUsefulEpisodeTitle(normalizedTitle: String): Boolean {
    if (normalizedTitle.isBlank()) return false
    if (normalizedTitle.matches(Regex("episode \\d+"))) return false
    if (normalizedTitle.matches(Regex("ep \\d+"))) return false
    if (normalizedTitle.matches(Regex("e \\d+"))) return false
    return true
}
