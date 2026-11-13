package com.nuvio.tv.core.livetv

import java.text.Normalizer

/** Categories are accepted only from explicit addon genres or M3U group titles. */
internal enum class LiveTvChannelCategory {
    NEWS, ENTERTAINMENT, SPORTS, KIDS, MOVIES, MUSIC, DOCUMENTARY, OTHER, UNCATEGORIZED;

    companion object {
        fun fromGroup(group: String?): LiveTvChannelCategory {
            val key = group?.trim()?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
                ?.replace(Regex("\\p{Mn}+"), "")?.lowercase().orEmpty()
            return when (key) {
                "jornalismo", "noticias", "news", "information", "informacao" -> NEWS
                "entretenimento", "entertainment", "variedades", "variety" -> ENTERTAINMENT
                "esportes", "esporte", "desporto", "sports", "sport" -> SPORTS
                "infantil", "kids", "children" -> KIDS
                "filmes", "movies", "cinema" -> MOVIES
                "musica", "music" -> MUSIC
                "documentarios", "documentary", "documentaries" -> DOCUMENTARY
                "", "uncategorized" -> UNCATEGORIZED
                "other" -> OTHER
                else -> OTHER
            }
        }

        fun fromGenres(genres: List<String>): LiveTvChannelCategory {
            if (genres.isEmpty()) return UNCATEGORIZED
            val explicit = genres.map(::fromGroup).filter { it != OTHER && it != UNCATEGORIZED }.distinct()
            return when (explicit.size) {
                1 -> explicit.single()
                0 -> if (genres.all { fromGroup(it) == OTHER }) OTHER else UNCATEGORIZED
                else -> UNCATEGORIZED
            }
        }
    }
}
