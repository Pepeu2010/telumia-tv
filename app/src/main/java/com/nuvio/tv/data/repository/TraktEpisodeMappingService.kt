package com.nuvio.tv.data.repository

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.runtime.DeviceCapabilities
import com.nuvio.tv.core.storage.TtlLruCache
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.repository.MetaRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktEpisodeMappingService @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val traktApi: TraktApi,
    private val traktAuthService: TraktAuthService,
    private val metaRepository: MetaRepository
) {
    companion object {
        private const val TAG = "TraktEpMapSvc"
        private const val CACHE_TTL_MILLIS = 10 * 60_000L
    }

    internal suspend fun isTraktAuthenticated(): Boolean =
        traktAuthService.getCurrentAuthState().isAuthenticated

    private val cacheMutex = Mutex()
    private val cacheLimits = TraktEpisodeMappingCacheLimits.forTier(
        DeviceCapabilities.resourceTier(DeviceCapabilities.memory(appContext)),
    )
    private val titleNormalizer = EpisodeTitleNormalizer(cacheLimits.normalizedEpisodeTitles)
    private val mappingCache = TtlLruCache<String, EpisodeMappingEntry>(
        capacity = cacheLimits.remappedEpisodes,
        ttlMillis = CACHE_TTL_MILLIS,
    )
    private val addonEpisodesCache = TtlLruCache<String, List<EpisodeMappingEntry>>(
        capacity = cacheLimits.addonEpisodeLists,
        ttlMillis = CACHE_TTL_MILLIS,
    )
    private val traktEpisodesCache = TtlLruCache<String, List<EpisodeMappingEntry>>(
        capacity = cacheLimits.traktEpisodeLists,
        ttlMillis = CACHE_TTL_MILLIS,
    )
    private val reverseMappingCache = TtlLruCache<String, EpisodeMappingEntry>(
        capacity = cacheLimits.reverseRemaps,
        ttlMillis = CACHE_TTL_MILLIS,
    )
    // In-flight dedup: prevents multiple concurrent coroutines from fetching
    // the same show's addon episodes simultaneously.
    private val addonEpisodesInFlight = mutableMapOf<String, CompletableDeferred<List<EpisodeMappingEntry>>>()
    private val traktEpisodesInFlight = mutableMapOf<String, CompletableDeferred<List<EpisodeMappingEntry>>>()

    internal suspend fun prefetchEpisodeMapping(
        contentId: String?,
        contentType: String?,
        videoId: String?,
        season: Int?,
        episode: Int?
    ): EpisodeMappingEntry? {
        return resolveEpisodeMapping(contentId, contentType, videoId, season, episode)
    }

    /**
     * Pre-fetches addon episode data for [contentIds] so that subsequent calls to
     * [resolveAddonEpisodeMapping] hit the cache. Limits concurrency to [concurrency].
     */
    internal suspend fun prefetchAddonEpisodes(
        contentIds: List<String>,
        concurrency: Int = 8
    ) {
        val unique = contentIds.distinct()
        if (unique.isEmpty()) return
        val semaphore = Semaphore(concurrency)
        supervisorScope {
            unique.forEach { contentId ->
                launch {
                    try {
                        semaphore.withPermit {
                            getAddonEpisodes(contentId, "series")
                        }
                    } catch (_: Exception) {
                        // Individual prefetch failures are non-fatal. The mapping phase
                        // will retry or proceed without cached data for this show.
                    }
                }
            }
        }
    }

    internal suspend fun resolveAddonEpisodeMapping(
        contentId: String?,
        contentType: String?,
        season: Int?,
        episode: Int?,
        episodeTitle: String? = null
    ): EpisodeMappingEntry? {
        if (!traktAuthService.getCurrentAuthState().isAuthenticated) return null

        val requestedSeason = season ?: return null
        val requestedEpisode = episode ?: return null
        val resolvedContentId = contentId?.takeIf { it.isNotBlank() } ?: return null
        val resolvedContentType = contentType?.takeIf { it.isNotBlank() } ?: return null
        val reverseKey = reverseCacheKey(
            contentId = resolvedContentId,
            contentType = resolvedContentType,
            season = requestedSeason,
            episode = requestedEpisode,
            title = episodeTitle
        )
        cacheMutex.withLock {
            reverseMappingCache.get(reverseKey)?.let { return it }
        }

        val addonEpisodes = getAddonEpisodes(resolvedContentId, resolvedContentType)
        if (addonEpisodes.isEmpty()) return null

        val showLookupId = resolveShowLookupId(contentId = resolvedContentId, videoId = null) ?: return null
        val traktEpisodes = getTraktEpisodes(showLookupId)
        if (traktEpisodes.isEmpty()) return null

        val addonHasEpisode = addonEpisodes.any {
            it.season == requestedSeason && it.episode == requestedEpisode
        }
        val sameStructure = hasSameSeasonStructure(addonEpisodes, traktEpisodes)
        if (addonHasEpisode && sameStructure) {
            return null
        }

        val mapped = reverseRemapEpisodeByTitleOrIndex(
            requestedSeason = requestedSeason,
            requestedEpisode = requestedEpisode,
            requestedTitle = episodeTitle,
            addonEpisodes = addonEpisodes,
            traktEpisodes = traktEpisodes,
            titleNormalizer = titleNormalizer,
        ) ?: return null

        cacheMutex.withLock {
            reverseMappingCache.put(reverseKey, mapped)
        }
        return mapped
    }

    private fun hasSameSeasonStructure(
        addonEpisodes: List<EpisodeMappingEntry>,
        traktEpisodes: List<EpisodeMappingEntry>
    ): Boolean {
        // Compare per-season episode counts, not just the set of season numbers.
        // Anime often uses the same season numbers in both sources but with completely
        // different episode distributions.
        val addonPerSeason = addonEpisodes.groupBy { it.season }.mapValues { it.value.size }
        val traktPerSeason = traktEpisodes.groupBy { it.season }.mapValues { it.value.size }
        return addonPerSeason == traktPerSeason
    }

    internal suspend fun getCachedEpisodeMapping(
        contentId: String?,
        contentType: String?,
        videoId: String?,
        season: Int?,
        episode: Int?
    ): EpisodeMappingEntry? {
        val key = cacheKey(contentId, contentType, videoId, season, episode) ?: return null
        return cacheMutex.withLock { mappingCache.get(key) }
    }

    internal suspend fun resolveEpisodeMapping(
        contentId: String?,
        contentType: String?,
        videoId: String?,
        season: Int?,
        episode: Int?,
        episodeTitle: String? = null
    ): EpisodeMappingEntry? {
        if (!traktAuthService.getCurrentAuthState().isAuthenticated) return null

        val key = cacheKey(contentId, contentType, videoId, season, episode) ?: return null
        cacheMutex.withLock {
            mappingCache.get(key)?.let { return it }
        }

        val requestedSeason = season ?: return null
        val requestedEpisode = episode ?: return null
        val resolvedContentId = contentId?.takeIf { it.isNotBlank() } ?: return null
        val resolvedContentType = contentType?.takeIf { it.isNotBlank() } ?: return null

        val addonEpisodes = getAddonEpisodes(resolvedContentId, resolvedContentType)
        if (addonEpisodes.isEmpty()) return null

        val showLookupId = resolveShowLookupId(contentId = resolvedContentId, videoId = videoId) ?: return null
        val traktEpisodes = getTraktEpisodes(showLookupId)
        if (traktEpisodes.isEmpty()) return null

        if (hasSameSeasonStructure(addonEpisodes, traktEpisodes)) {
            return null
        }

        val mapped = remapEpisodeByTitleOrIndex(
            requestedSeason = requestedSeason,
            requestedEpisode = requestedEpisode,
            requestedVideoId = videoId,
            requestedTitle = episodeTitle,
            addonEpisodes = addonEpisodes,
            traktEpisodes = traktEpisodes,
            titleNormalizer = titleNormalizer,
        ) ?: return null

        cacheMutex.withLock {
            mappingCache.put(key, mapped)
        }
        return mapped
    }

    private suspend fun getAddonEpisodes(
        contentId: String,
        contentType: String
    ): List<EpisodeMappingEntry> {
        val cacheKey = addonEpisodesCacheKey(contentId, contentType)

        // Fast path: cache hit
        cacheMutex.withLock {
            addonEpisodesCache.get(cacheKey)?.let { return it }
        }

        // Dedup: if another coroutine is already fetching this show, await its result.
        val existingDeferred = cacheMutex.withLock { addonEpisodesInFlight[cacheKey] }
        if (existingDeferred != null) {
            return try { existingDeferred.await() } catch (_: Exception) { emptyList() }
        }

        // Register ourselves as the in-flight fetcher.
        val deferred = kotlinx.coroutines.CompletableDeferred<List<EpisodeMappingEntry>>()
        val weOwn = cacheMutex.withLock {
            // Double-check: cache or another flight may have appeared while we waited.
            addonEpisodesCache.get(cacheKey)?.let { return it }
            if (addonEpisodesInFlight.containsKey(cacheKey)) {
                false
            } else {
                addonEpisodesInFlight[cacheKey] = deferred
                true
            }
        }
        if (!weOwn) {
            val other = cacheMutex.withLock { addonEpisodesInFlight[cacheKey] }
            return try { other?.await() ?: emptyList() } catch (_: Exception) { emptyList() }
        }

        return try {
            val meta = fetchSeriesMeta(contentId, contentType)
            val addonEpisodes = meta?.videos?.toEpisodeMappingEntries() ?: emptyList()
            if (addonEpisodes.isNotEmpty()) {
                cacheMutex.withLock { addonEpisodesCache.put(cacheKey, addonEpisodes) }
            }
            deferred.complete(addonEpisodes)
            addonEpisodes
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
            emptyList()
        } finally {
            cacheMutex.withLock { addonEpisodesInFlight.remove(cacheKey) }
        }
    }

    private suspend fun getTraktEpisodes(showLookupId: String): List<EpisodeMappingEntry> {
        cacheMutex.withLock {
            traktEpisodesCache.get(showLookupId)?.let { return it }
        }

        val existingDeferred = cacheMutex.withLock { traktEpisodesInFlight[showLookupId] }
        if (existingDeferred != null) {
            return try { existingDeferred.await() } catch (_: Exception) { emptyList() }
        }

        val deferred = CompletableDeferred<List<EpisodeMappingEntry>>()
        val weOwn = cacheMutex.withLock {
            traktEpisodesCache.get(showLookupId)?.let { return it }
            if (traktEpisodesInFlight.containsKey(showLookupId)) {
                false
            } else {
                traktEpisodesInFlight[showLookupId] = deferred
                true
            }
        }
        if (!weOwn) {
            val other = cacheMutex.withLock { traktEpisodesInFlight[showLookupId] }
            return try { other?.await() ?: emptyList() } catch (_: Exception) { emptyList() }
        }

        val seasonsResponse = traktAuthService.executeAuthorizedRequest { authHeader ->
            traktApi.getShowSeasons(
                authorization = authHeader,
                id = showLookupId,
                extended = "episodes"
            )
        } ?: run {
            cleanupTraktFlight(showLookupId)
            return emptyList()
        }
        if (!seasonsResponse.isSuccessful) {
            Log.w(
                TAG,
                "getTraktEpisodes: seasons request failed code=${seasonsResponse.code()} id=$showLookupId"
            )
            cleanupTraktFlight(showLookupId)
            return emptyList()
        }

        val traktEpisodes = seasonsResponse.body()
            .orEmpty()
            .asSequence()
            .filter { (it.number ?: 0) > 0 }
            .sortedBy { it.number }
            .flatMap { seasonDto ->
                seasonDto.episodes.orEmpty().asSequence()
                    .mapNotNull { episodeDto ->
                        val seasonNumber = episodeDto.season ?: seasonDto.number ?: return@mapNotNull null
                        val episodeNumber = episodeDto.number ?: return@mapNotNull null
                        EpisodeMappingEntry(
                            season = seasonNumber,
                            episode = episodeNumber,
                            title = episodeDto.title
                        )
                    }
            }
            .toList()

        if (traktEpisodes.isNotEmpty()) {
            cacheMutex.withLock {
                traktEpisodesCache.put(showLookupId, traktEpisodes)
            }
        }
        deferred.complete(traktEpisodes)
        cleanupTraktFlight(showLookupId)
        return traktEpisodes
    }

    private suspend fun cleanupTraktFlight(showLookupId: String) {
        cacheMutex.withLock { traktEpisodesInFlight.remove(showLookupId) }
    }

    private fun cacheKey(
        contentId: String?,
        contentType: String?,
        videoId: String?,
        season: Int?,
        episode: Int?
    ): String? {
        val resolvedContentId = contentId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val resolvedContentType = contentType?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val resolvedSeason = season ?: return null
        val resolvedEpisode = episode ?: return null
        val resolvedVideoId = videoId?.trim().orEmpty()
        return "$resolvedContentType|$resolvedContentId|$resolvedVideoId|$resolvedSeason|$resolvedEpisode"
    }

    private fun reverseCacheKey(
        contentId: String,
        contentType: String,
        season: Int,
        episode: Int,
        title: String?
    ): String {
        val normalizedTitle = title?.trim()?.lowercase().orEmpty()
        return "reverse|${contentType.trim().lowercase()}|${contentId.trim()}|$season|$episode|$normalizedTitle"
    }

    private fun addonEpisodesCacheKey(contentId: String, contentType: String): String {
        return "${contentType.trim().lowercase()}|${contentId.trim()}"
    }

    private fun resolveShowLookupId(contentId: String?, videoId: String?): String? {
        val contentIds = toTraktIds(parseContentIds(contentId))
        if (contentIds.hasAnyId()) {
            return when {
                !contentIds.imdb.isNullOrBlank() -> contentIds.imdb
                contentIds.trakt != null -> contentIds.trakt.toString()
                !contentIds.slug.isNullOrBlank() -> contentIds.slug
                else -> null
            }
        }

        val videoIds = toTraktIds(parseContentIds(videoId))
        return when {
            !videoIds.imdb.isNullOrBlank() -> videoIds.imdb
            videoIds.trakt != null -> videoIds.trakt.toString()
            !videoIds.slug.isNullOrBlank() -> videoIds.slug
            else -> null
        }
    }

    private suspend fun fetchSeriesMeta(contentId: String, contentType: String): Meta? {
        val typeCandidates = buildList {
            val normalized = contentType.lowercase()
            if (normalized.isNotBlank()) add(normalized)
            if (normalized in listOf("series", "tv")) {
                add("series")
                add("tv")
            }
        }.distinct()
        if (typeCandidates.isEmpty()) return null

        val idCandidates = buildList {
            add(contentId)
            if (contentId.startsWith("tmdb:")) add(contentId.substringAfter(':'))
            if (contentId.startsWith("trakt:")) add(contentId.substringAfter(':'))
        }.distinct()

        for (type in typeCandidates) {
            for (candidateId in idCandidates) {
                val result = try {
                    withTimeoutOrNull(8000) {
                        metaRepository.getMetaFromAllAddons(type = type, id = candidateId)
                            .dropWhile { it is NetworkResult.Loading }
                            .firstOrNull()
                    }
                } catch (_: Exception) {
                    null
                } ?: continue
                val meta = (result as? NetworkResult.Success)?.data ?: continue
                if (meta.videos.any { it.season != null && it.episode != null }) {
                    return meta
                }
            }
        }
        return null
    }

    private fun List<Video>.toEpisodeMappingEntries(): List<EpisodeMappingEntry> {
        return asSequence()
            .mapNotNull { video ->
                val season = video.season ?: return@mapNotNull null
                val episode = video.episode ?: return@mapNotNull null
                if (season <= 0) return@mapNotNull null
                EpisodeMappingEntry(
                    season = season,
                    episode = episode,
                    title = video.title,
                    videoId = video.id.takeIf { it.isNotBlank() }
                )
            }
            .distinctBy { it.videoId ?: "${it.season}:${it.episode}" }
            .sortedWith(compareBy(EpisodeMappingEntry::season, EpisodeMappingEntry::episode))
            .toList()
    }
}
