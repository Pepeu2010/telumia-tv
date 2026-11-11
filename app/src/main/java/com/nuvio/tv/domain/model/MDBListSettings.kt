package com.nuvio.tv.domain.model

data class MDBListSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val showImdb: Boolean = true,
    val showTmdb: Boolean = true,
    val showLetterboxd: Boolean = true,
    val showTomatoes: Boolean = true,
    val showAudience: Boolean = true,
    val showMetacritic: Boolean = true,
    val showMal: Boolean = true,
    val showOnHero: Boolean = false,
    val ratingOrder: List<String> = DEFAULT_RATING_ORDER
) {
    fun hasEnabledProviders(): Boolean =
        showImdb || showTmdb || showLetterboxd ||
            showTomatoes || showAudience || showMetacritic || showMal

    /** Returns the rating order filtered to only include enabled providers. */
    fun enabledRatingOrder(): List<String> = ratingOrder.filter { provider ->
        when (provider) {
            "imdb" -> showImdb
            "tmdb" -> showTmdb
            "letterboxd" -> showLetterboxd
            "tomatoes" -> showTomatoes
            "audience" -> showAudience
            "metacritic" -> showMetacritic
            "mal" -> showMal
            else -> false
        }
    }

    companion object {
        val DEFAULT_RATING_ORDER = listOf(
            "imdb", "tmdb", "tomatoes", "audience",
            "letterboxd", "metacritic", "mal"
        )
    }
}
