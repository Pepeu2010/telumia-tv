package com.nuvio.tv.core.tmdb

import com.nuvio.tv.BuildConfig

/**
 * The TMDB key used for every TMDB request: the one the user entered in Settings, or the key the
 * build shipped with. Released builds ship without one, so company/collection search and TMDB
 * metadata work once the user adds their own (free) key.
 */
object TmdbApiKeyHolder {
    @Volatile
    var userKey: String = ""

    fun effective(): String = userKey.trim().ifBlank { BuildConfig.TMDB_API_KEY.trim() }
}
