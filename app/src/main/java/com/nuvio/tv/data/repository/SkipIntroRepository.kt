package com.nuvio.tv.data.repository

import android.content.Context
import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.runtime.DeviceCapabilities
import com.nuvio.tv.core.runtime.DeviceResourceTier
import com.nuvio.tv.core.storage.TtlLruCache
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.AnimeSkipSettingsDataStore
import com.nuvio.tv.data.remote.api.AniSkipApi
import com.nuvio.tv.data.remote.api.AnimeSkipApi
import com.nuvio.tv.data.remote.api.AnimeSkipRequest
import com.nuvio.tv.data.remote.api.IntroDbApi
import com.nuvio.tv.data.remote.api.IntroDbSegment
import com.nuvio.tv.data.remote.api.IntroDbSegmentsResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import dagger.hilt.android.qualifiers.ApplicationContext

/** Initial retained-entry and freshness budgets for timed skip metadata. */
internal data class SkipIntroCacheLimits(
    val intervals: Int,
    val animeSkipShowIds: Int,
    val intervalTtlMillis: Long = INTERVAL_TTL_MILLIS,
    val animeSkipShowIdTtlMillis: Long = SHOW_ID_TTL_MILLIS,
) {
    init {
        require(intervals > 0 && animeSkipShowIds > 0) { "Skip intro cache limits must be positive" }
        require(intervalTtlMillis > 0L && animeSkipShowIdTtlMillis > 0L) {
            "Skip intro cache TTLs must be positive"
        }
    }

    companion object {
        const val INTERVAL_TTL_MILLIS = 12L * 60L * 60L * 1_000L
        const val SHOW_ID_TTL_MILLIS = 7L * 24L * 60L * 60L * 1_000L

        fun forTier(tier: DeviceResourceTier): SkipIntroCacheLimits = when (tier) {
            DeviceResourceTier.LOW_RAM, DeviceResourceTier.UNKNOWN -> SkipIntroCacheLimits(
                intervals = 32,
                animeSkipShowIds = 24,
            )
            DeviceResourceTier.BALANCED -> SkipIntroCacheLimits(
                intervals = 64,
                animeSkipShowIds = 48,
            )
            DeviceResourceTier.HIGH_CAPACITY -> SkipIntroCacheLimits(
                intervals = 128,
                animeSkipShowIds = 96,
            )
        }
    }
}

/** Keeps repository cache mechanics independently testable without persisting provider data. */
internal class SkipIntroCache(
    limits: SkipIntroCacheLimits,
    nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val intervals = TtlLruCache<String, List<SkipInterval>>(
        capacity = limits.intervals,
        ttlMillis = limits.intervalTtlMillis,
        nowMillis = nowMillis,
    )
    private val animeSkipShowIds = TtlLruCache<String, String>(
        capacity = limits.animeSkipShowIds,
        ttlMillis = limits.animeSkipShowIdTtlMillis,
        nowMillis = nowMillis,
    )

    fun getIntervals(key: String): List<SkipInterval>? = intervals.get(key)
    fun putIntervals(key: String, value: List<SkipInterval>) = intervals.put(key, value)
    fun getAnimeSkipShowId(anilistId: String): String? = animeSkipShowIds.get(anilistId)
    fun putAnimeSkipShowId(anilistId: String, showId: String) = animeSkipShowIds.put(anilistId, showId)

    val intervalSize: Int get() = intervals.size
    val animeSkipShowIdSize: Int get() = animeSkipShowIds.size
}

private data class SkipProviderResult(
    val intervals: List<SkipInterval>,
    /** False means a provider failed transiently, so the aggregate must remain retryable. */
    val cacheable: Boolean = true,
)

data class SkipInterval(
    val startTime: Double, // seconds
    val endTime: Double,   // seconds
    val type: String,      // intro/op, recap, outro/ed, movie-credits, post-credits
    val provider: String   // "introdb", "aniskip", "animeskip"
)

internal fun IntroDbSegmentsResponse.toSkipIntervals(movie: Boolean): List<SkipInterval> {
    if (!movie) return listOfNotNull(
        intro.toSkipIntervalOrNull("intro"),
        recap.toSkipIntervalOrNull("recap"),
        outro.toSkipIntervalOrNull("outro")
    )
    val credits = outro.toSkipIntervalOrNull("movie-credits")
    val scene = postCredits.toSkipIntervalOrNull("post-credits")
    // Skipping credits must not also skip the post-credits scene.
    val safeCredits = if (credits != null && scene != null &&
        scene.startTime < credits.endTime && scene.endTime > credits.startTime
    ) {
        credits.copy(endTime = scene.startTime).takeIf { it.endTime > it.startTime }
    } else credits
    return listOfNotNull(safeCredits, scene)
}

private fun IntroDbSegment?.toSkipIntervalOrNull(type: String): SkipInterval? {
    if (this == null) return null
    val start = startSec ?: startMs?.let { it / 1000.0 } ?: return null
    val end = endSec ?: endMs?.let { it / 1000.0 } ?: return null
    if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start) return null
    return SkipInterval(startTime = start, endTime = end, type = type, provider = "introdb")
}

@Singleton
class SkipIntroRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val introDbApi: IntroDbApi,
    private val aniSkipApi: AniSkipApi,
    private val animeSkipApi: AnimeSkipApi,
    private val simklResolver: SimklIdResolver,
    private val animeSkipSettingsDataStore: AnimeSkipSettingsDataStore,
    private val tmdbService: TmdbService
) {
    private val cache = SkipIntroCache(
        SkipIntroCacheLimits.forTier(DeviceCapabilities.resourceTier(DeviceCapabilities.memory(appContext))),
    )
    private val introDbConfigured = BuildConfig.INTRODB_API_URL.isNotEmpty()

    suspend fun getMovieSkipIntervals(contentId: String?, videoId: String? = null): List<SkipInterval> {
        if (!introDbConfigured) return emptyList()
        val ids = listOfNotNull(contentId, videoId).distinct()
        val imdbId = ids.firstNotNullOfOrNull { id ->
            id.substringBefore(':').takeIf { it.matches(Regex("tt[0-9]+")) }
        } ?: ids.firstNotNullOfOrNull { id ->
            val parts = id.split(':')
            val value = parts.getOrNull(1) ?: return@firstNotNullOfOrNull null
            when (parts[0]) {
                "tmdb" -> value.toIntOrNull()?.let { tmdbService.tmdbToImdb(it, "movie") }
                "mal", "kitsu" -> simklResolver.resolveIds(parts[0], value)?.imdb
                else -> null
            }
        } ?: return emptyList()
        val key = "movie:$imdbId"
        cache.getIntervals(key)?.let { return it }
        val result = fetchFromIntroDb(imdbId, isMovie = true)
        if (result.cacheable) cache.putIntervals(key, result.intervals)
        return result.intervals
    }

    /**
     * Standard path for IMDB-identified content.
     */
    suspend fun getSkipIntervals(imdbId: String?, season: Int, episode: Int): List<SkipInterval> = coroutineScope {
        if (imdbId == null) return@coroutineScope emptyList()
        val cacheKey = "$imdbId:$season:$episode"
        cache.getIntervals(cacheKey)?.let { return@coroutineScope it }

        val introDbDeferred = async {
            if (introDbConfigured) fetchFromIntroDb(imdbId, season, episode) else SkipProviderResult(emptyList())
        }
        // Resolve IMDB -> season-specific MAL/AniList via Simkl episode mapping
        val simklIdsDeferred = async { simklResolver.resolveIdsForImdbEpisode(imdbId, season, episode) }
        val simklIds = simklIdsDeferred.await()
        val malId = simklIds?.mal
        val anilistId = simklIds?.anilist

        // Remap the TVDB episode number to the anime-entry-local episode number.
        // When the resolved entry owns a specific TVDB season, its episode mapping
        // tells us which anime episode corresponds to the requested TVDB episode.
        val animeEpisode = if (simklIds != null) {
            val mapping = simklResolver.getEpisodeMapping(simklIds.simklId, simklIds.type)
            mapping.firstOrNull { it.tvdbSeason == season && it.tvdbEpisode == episode }
                ?.animeEpisode
                ?: episode
        } else episode

        val aniSkipDeferred = async {
            if (malId != null) fetchFromAniSkip(malId, animeEpisode) else SkipProviderResult(emptyList())
        }
        val animeSkipDeferred = async {
            if (anilistId != null) fetchFromAnimeSkip(anilistId, animeEpisode, season = null)
            else SkipProviderResult(emptyList())
        }

        return@coroutineScope mergeAndCache(
            cacheKey,
            introDbDeferred.await(),
            animeSkipDeferred.await(),
            aniSkipDeferred.await(),
        )
    }

    suspend fun getSkipIntervalsForMal(
        malId: String,
        episode: Int,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null
    ): List<SkipInterval> = coroutineScope {
        val cacheKey = "mal:$malId:$episode"
        cache.getIntervals(cacheKey)?.let { return@coroutineScope it }

        val aniSkipDeferred = async { fetchFromAniSkip(malId, episode) }

        val simklIdsDeferred = async { simklResolver.resolveIds("mal", malId) }
        val simklIds = simklIdsDeferred.await()
        val resolvedImdbId = imdbId ?: simklIds?.imdb

        val tvdbDeferred = async {
            if (resolvedImdbId != null && imdbSeason == null && simklIds != null) {
                simklResolver.resolveEpisodeTvdb("mal", malId, episode)
            } else null
        }

        var introDb = SkipProviderResult(emptyList())
        var animeSkip = SkipProviderResult(emptyList())
        if (resolvedImdbId != null) {
            val tvdb = tvdbDeferred.await()
            val introDbSeason = imdbSeason ?: tvdb?.first ?: return@coroutineScope run {
                mergeAndCache(cacheKey, animeSkip, aniSkipDeferred.await())
            }
            val introDbEpisode = imdbEpisode ?: tvdb?.second ?: episode
            val introDbDeferred = async {
                if (introDbConfigured) fetchFromIntroDb(resolvedImdbId, introDbSeason, introDbEpisode)
                else SkipProviderResult(emptyList())
            }
            val anilistId = simklIds?.anilist
            val animeSkipDeferred = async {
                if (anilistId != null) fetchFromAnimeSkip(anilistId, episode, season = null)
                else SkipProviderResult(emptyList())
            }
            introDb = introDbDeferred.await()
            animeSkip = animeSkipDeferred.await()
        } else {
            val anilistId = simklIds?.anilist
            if (anilistId != null) animeSkip = fetchFromAnimeSkip(anilistId, episode, season = null)
        }

        return@coroutineScope mergeAndCache(cacheKey, introDb, animeSkip, aniSkipDeferred.await())
    }

    suspend fun getSkipIntervalsForKitsu(
        kitsuId: String,
        episode: Int,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null
    ): List<SkipInterval> = coroutineScope {
        val cacheKey = "kitsu:$kitsuId:$episode"
        cache.getIntervals(cacheKey)?.let { return@coroutineScope it }

        // Resolve all IDs via Simkl
        val simklIdsDeferred = async { simklResolver.resolveIds("kitsu", kitsuId) }
        val simklIds = simklIdsDeferred.await()
        val malIdStr = simklIds?.mal
        val resolvedImdbId = imdbId ?: simklIds?.imdb

        val aniSkipDeferred = async {
            if (malIdStr != null) fetchFromAniSkip(malIdStr, episode) else SkipProviderResult(emptyList())
        }

        val tvdbDeferred = async {
            if (resolvedImdbId != null && imdbSeason == null && simklIds != null) {
                simklResolver.resolveEpisodeTvdb("kitsu", kitsuId, episode)
            } else null
        }

        var introDb = SkipProviderResult(emptyList())
        var animeSkip = SkipProviderResult(emptyList())
        if (resolvedImdbId != null) {
            val tvdb = tvdbDeferred.await()
            val introDbSeason = imdbSeason ?: tvdb?.first ?: return@coroutineScope run {
                mergeAndCache(cacheKey, animeSkip, aniSkipDeferred.await())
            }
            val introDbEpisode = imdbEpisode ?: tvdb?.second ?: episode
            val introDbDeferred = async {
                if (introDbConfigured) fetchFromIntroDb(resolvedImdbId, introDbSeason, introDbEpisode)
                else SkipProviderResult(emptyList())
            }
            val anilistId = simklIds?.anilist
            val animeSkipDeferred = async {
                if (anilistId != null) fetchFromAnimeSkip(anilistId, episode, season = null)
                else SkipProviderResult(emptyList())
            }
            introDb = introDbDeferred.await()
            animeSkip = animeSkipDeferred.await()
        } else {
            val anilistId = simklIds?.anilist
            if (anilistId != null) animeSkip = fetchFromAnimeSkip(anilistId, episode, season = null)
        }

        return@coroutineScope mergeAndCache(cacheKey, introDb, animeSkip, aniSkipDeferred.await())
    }

    /**
     * Merge provider results into one best-of: fill each segment category (opening / ending /
     * recap) from the highest-priority provider that has it. Arguments MUST be passed in priority
     * order (IntroDB, then Anime-Skip, then AniSkip as fallback),
     * so a partial result from one provider never shadows a complete segment from another.
     */
    private fun mergeByPriority(vararg providerResults: List<SkipInterval>): List<SkipInterval> {
        val chosen = LinkedHashMap<String, SkipInterval>()
        for (result in providerResults) {
            for (interval in result) {
                val category = segmentCategory(interval.type) ?: continue
                chosen.putIfAbsent(category, interval)
            }
        }
        return chosen.values.toList()
    }

    private fun mergeAndCache(cacheKey: String, vararg providerResults: SkipProviderResult): List<SkipInterval> {
        val merged = mergeByPriority(*providerResults.map { it.intervals }.toTypedArray())
        if (providerResults.all { it.cacheable }) cache.putIntervals(cacheKey, merged)
        return merged
    }

    private fun segmentCategory(type: String): String? = when (type.lowercase()) {
        "intro", "op", "mixed-op" -> "opening"
        "outro", "ed", "mixed-ed", "credits", "ending" -> "ending"
        "recap" -> "recap"
        else -> null
    }

    private suspend fun fetchFromIntroDb(
        imdbId: String,
        season: Int? = null,
        episode: Int? = null,
        isMovie: Boolean = false
    ): SkipProviderResult {
        return try {
            val response = introDbApi.getSegments(imdbId, season, episode, true.takeIf { isMovie })
            when {
                response.isSuccessful -> response.body()?.let { SkipProviderResult(it.toSkipIntervals(isMovie)) }
                    ?: SkipProviderResult(emptyList(), cacheable = false)
                response.code() == 404 -> SkipProviderResult(emptyList())
                else -> SkipProviderResult(emptyList(), cacheable = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("SkipIntro", "IntroDB: no data for $imdbId S${season}E${episode}")
            SkipProviderResult(emptyList(), cacheable = false)
        }
    }

    private suspend fun fetchFromAniSkip(malId: String, episode: Int): SkipProviderResult {
        return try {
            val types = listOf("op", "ed", "recap", "mixed-op", "mixed-ed")
            val response = aniSkipApi.getSkipTimes(malId, episode, types)
            when {
                response.isSuccessful -> {
                    val body = response.body() ?: return SkipProviderResult(emptyList(), cacheable = false)
                    val intervals = if (body.found) {
                        body.results?.map { result ->
                            SkipInterval(
                                startTime = result.interval.startTime,
                                endTime = result.interval.endTime,
                                type = result.skipType,
                                provider = "aniskip"
                            )
                        } ?: emptyList()
                    } else emptyList()
                    SkipProviderResult(intervals)
                }
                response.code() == 404 -> SkipProviderResult(emptyList())
                else -> SkipProviderResult(emptyList(), cacheable = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("SkipIntro", "AniSkip: no data for MAL $malId ep $episode")
            SkipProviderResult(emptyList(), cacheable = false)
        }
    }

    // season: null when anilistId is season-specific; pass season number when using fallback ID
    private suspend fun fetchFromAnimeSkip(anilistId: String, episode: Int, season: Int?): SkipProviderResult {
        val clientId = animeSkipSettingsDataStore.clientId.firstOrNull()?.trim()
        if (clientId.isNullOrBlank()) return SkipProviderResult(emptyList())
        val enabled = animeSkipSettingsDataStore.enabled.firstOrNull() ?: false
        if (!enabled) return SkipProviderResult(emptyList())
        return try {
            val showIdsResult = resolveAnimeSkipShowIds(anilistId, clientId)
            if (!showIdsResult.cacheable) return SkipProviderResult(emptyList(), cacheable = false)
            val showIds = showIdsResult.showIds
            if (showIds.isEmpty()) return SkipProviderResult(emptyList())

            var hadTransientFailure = false
            for (showId in showIds) {
                val episodesResponse = try {
                    animeSkipApi.query(
                        clientId = clientId,
                        body = AnimeSkipRequest(
                            query = "{ findEpisodesByShowId(showId: \"$showId\") { season number timestamps { at type { name } } } }"
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    hadTransientFailure = true
                    continue
                }
                if (!episodesResponse.isSuccessful) {
                    hadTransientFailure = true
                    continue
                }

                val responseBody = episodesResponse.body()
                if (responseBody?.data == null) {
                    hadTransientFailure = true
                    continue
                }
                val episodes = responseBody.data.findEpisodesByShowId ?: continue
                val targetEpisode = episodes.firstOrNull { ep ->
                    ep.number?.toIntOrNull() == episode &&
                        (season == null || ep.season?.toIntOrNull() == season)
                } ?: continue

                val sorted = (targetEpisode.timestamps ?: continue).sortedBy { it.at }
                val result = sorted.mapIndexedNotNull { i, ts ->
                    val endTime = sorted.getOrNull(i + 1)?.at ?: Double.MAX_VALUE
                    val type = when (ts.type.name.lowercase()) {
                        "intro", "new intro" -> "op"
                        "credits", "new credits" -> "ed"
                        "mixed intro" -> "mixed-op"
                        "mixed credits" -> "mixed-ed"
                        "recap" -> "recap"
                        else -> return@mapIndexedNotNull null
                    }
                    SkipInterval(startTime = ts.at, endTime = endTime, type = type, provider = "animeskip")
                }
                if (result.isNotEmpty()) return SkipProviderResult(result, cacheable = !hadTransientFailure)
            }
            SkipProviderResult(emptyList(), cacheable = !hadTransientFailure)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("SkipIntro", "AnimeSkip: error for anilist $anilistId ep $episode: ${e.message}")
            SkipProviderResult(emptyList(), cacheable = false)
        }
    }

    private data class AnimeSkipShowIdsResult(
        val showIds: List<String>,
        val cacheable: Boolean = true,
    )

    private suspend fun resolveAnimeSkipShowIds(anilistId: String, clientId: String): AnimeSkipShowIdsResult {
        cache.getAnimeSkipShowId(anilistId)?.let { cached ->
            return AnimeSkipShowIdsResult(if (cached == NO_ID) emptyList() else listOf(cached))
        }
        val result = try {
            val response = animeSkipApi.query(
                clientId = clientId,
                body = AnimeSkipRequest(
                    query = "{ findShowsByExternalId(service: ANILIST, serviceId: \"$anilistId\") { id } }"
                )
            )
            when {
                !response.isSuccessful -> AnimeSkipShowIdsResult(emptyList(), cacheable = false)
                response.body()?.data == null -> AnimeSkipShowIdsResult(emptyList(), cacheable = false)
                else -> AnimeSkipShowIdsResult(
                    response.body()!!.data!!.findShowsByExternalId?.map { it.id } ?: emptyList(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AnimeSkipShowIdsResult(emptyList(), cacheable = false)
        }
        if (result.cacheable) {
            if (result.showIds.size == 1) cache.putAnimeSkipShowId(anilistId, result.showIds[0])
            else if (result.showIds.isEmpty()) cache.putAnimeSkipShowId(anilistId, NO_ID)
        }
        return result
    }

    companion object {
        private const val NO_ID = "__none__"
    }
}
