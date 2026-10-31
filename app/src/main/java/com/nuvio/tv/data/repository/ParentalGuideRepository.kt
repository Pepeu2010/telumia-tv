package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.core.storage.TtlLruCache
import com.nuvio.tv.data.remote.api.ImdbApiParentsGuideCategory
import com.nuvio.tv.data.remote.api.ParentalGuideApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolved parental guide data with a single severity per category,
 * determined by the highest-voted severity level from the API.
 */
data class ParentalGuideResult(
    val nudity: String? = null,
    val violence: String? = null,
    val profanity: String? = null,
    val alcohol: String? = null,
    val frightening: String? = null
)

internal data class ParentalGuideCacheLimits(
    val entries: Int = DEFAULT_ENTRIES,
    val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    init {
        require(entries > 0) { "Parental guide cache capacity must be positive" }
        require(ttlMillis > 0L) { "Parental guide cache TTL must be positive" }
    }

    companion object {
        const val DEFAULT_ENTRIES = 128
        const val DEFAULT_TTL_MILLIS = 12L * 60L * 60L * 1_000L
    }
}

/** Small in-memory cache for public IMDb parental-guide summaries. */
internal class ParentalGuideCache(
    limits: ParentalGuideCacheLimits = ParentalGuideCacheLimits(),
    nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val entries = TtlLruCache<String, ParentalGuideResult>(
        capacity = limits.entries,
        ttlMillis = limits.ttlMillis,
        nowMillis = nowMillis,
    )

    fun get(imdbId: String): ParentalGuideResult? = entries.get(imdbId)

    fun put(imdbId: String, result: ParentalGuideResult) = entries.put(imdbId, result)

    val size: Int get() = entries.size
}

@Singleton
class ParentalGuideRepository @Inject constructor(
    private val api: ParentalGuideApi
) {
    private val cache = ParentalGuideCache()

    suspend fun getParentalGuide(imdbId: String): ParentalGuideResult? {
        if (!imdbId.startsWith("tt")) return null

        cache.get(imdbId)?.let { return it }

        return try {
            val response = api.getParentsGuide(imdbId)
            if (!response.isSuccessful) return null
            val categories = response.body()?.parentsGuide.orEmpty()
            if (categories.isEmpty()) return null

            val result = mapToResult(categories)
            cache.put(imdbId, result)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ParentalGuide", "Failed to fetch parental guide for $imdbId", e)
            null
        }
    }

    private fun mapToResult(categories: List<ImdbApiParentsGuideCategory>): ParentalGuideResult {
        val categoryMap = categories.associateBy { it.category.uppercase() }

        return ParentalGuideResult(
            nudity = resolveSeverity(categoryMap["SEXUAL_CONTENT"]),
            violence = resolveSeverity(categoryMap["VIOLENCE"]),
            profanity = resolveSeverity(categoryMap["PROFANITY"]),
            alcohol = resolveSeverity(categoryMap["ALCOHOL_DRUGS"]),
            frightening = resolveSeverity(categoryMap["FRIGHTENING_INTENSE_SCENES"])
        )
    }

    /**
     * Determines the dominant severity level for a category by picking
     * the level with the highest vote count (excluding "none").
     * Returns null if no meaningful votes exist.
     */
    private fun resolveSeverity(category: ImdbApiParentsGuideCategory?): String? {
        if (category == null) return null

        val breakdowns = category.severityBreakdowns ?: return null

        // Find the severity with the most votes, excluding "none"
        val dominant = breakdowns
            .filter { it.severityLevel.lowercase() != "none" }
            .maxByOrNull { it.voteCount }

        // If "none" has more votes than any other severity, treat as no concern
        val noneVotes = breakdowns
            .firstOrNull { it.severityLevel.lowercase() == "none" }
            ?.voteCount ?: 0

        if (dominant == null || dominant.voteCount <= noneVotes) return null

        return dominant.severityLevel.lowercase()
    }
}
