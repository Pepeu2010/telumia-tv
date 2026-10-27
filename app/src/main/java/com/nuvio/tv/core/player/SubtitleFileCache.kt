package com.nuvio.tv.core.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.nuvio.tv.core.storage.MediaCacheCategory
import com.nuvio.tv.core.storage.tvMediaCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Downloads subtitle files from remote URLs to local cache and provides
 * content:// URIs via FileProvider so external players can read them.
 */
@Singleton
class SubtitleFileCache @Inject constructor(
    @ApplicationContext private val context: Context,
    // Callers pass addon-provided subtitle URLs, so use the permissive addon client.
    // Keep it that way: anything routed through here inherits permissive TLS.
    @param:Named("addonPermissive") private val okHttpClient: OkHttpClient
) {
    private val mediaCache = tvMediaCache(context)
    private val cache = BoundedSubtitleFileCache(
        directory = File(context.cacheDir, SUBTITLE_CACHE_DIR),
        quotaBytes = { mediaCache.cacheQuota(MediaCacheCategory.SUBTITLES) },
        canWriteEntry = { mediaCache.canWriteCacheEntry(MediaCacheCategory.SUBTITLES, it) },
    )

    /**
     * Downloads subtitle files and returns updated [SubtitleInput] list with
     * content:// URIs instead of HTTP URLs.
     *
     * Subtitles that fail to download are silently skipped (not included in result).
     * Returns null if no subtitles could be downloaded.
     */
    suspend fun cacheSubtitles(subtitles: List<SubtitleInput>): List<SubtitleInput>? {
        if (subtitles.isEmpty()) return null
        if (!mediaCache.canBeginCacheWrite(MediaCacheCategory.SUBTITLES)) return null

        // Clean old cached subtitles before downloading new ones
        clearCache()

        val cached = subtitles.mapNotNull { input ->
            try {
                val localUri = downloadToCache(input)
                if (localUri != null) {
                    input.copy(url = localUri.toString())
                } else {
                    Log.w(TAG, "Failed to download subtitle: ${input.name}")
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error caching subtitle: ${input.name}", e)
                null
            }
        }

        return cached.ifEmpty { null }
    }

    /**
     * Downloads a single subtitle file and returns a content:// URI.
     */
    private suspend fun downloadToCache(input: SubtitleInput): Uri? = withContext(Dispatchers.IO) {
        val extension = guessExtension(input.url)
        val filename = sanitizeFilename("${input.lang}_${input.name}.$extension")

        val request = Request.Builder()
            .url(input.url)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} downloading subtitle: ${input.url}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                if (body.contentLength() > BoundedSubtitleFileCache.MAX_ENTRY_BYTES) return@withContext null
                val bodyBytes = body.byteStream().use { BoundedSubtitleFileCache.readAtMost(it) }
                    ?: return@withContext null
                val normalizedText = SubtitleCharsetDetector.decode(bodyBytes, languageHint = input.lang)
                val sanitizedText = com.nuvio.tv.ui.screens.player.SubtitleMojibakeSanitizer.sanitize(normalizedText).toString()
                val file = cache.put(filename, sanitizedText.toByteArray(StandardCharsets.UTF_8))
                    ?: return@withContext null
                return@withContext FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download subtitle file: ${input.url}", e)
            null
        }
    }

    /** Stores normalized MPV sidecars under the same dynamic quota as external-player subtitles. */
    fun cacheNormalizedSubtitle(id: String, extension: String, text: String): File? {
        val normalizedExtension = extension.lowercase()
        if (normalizedExtension !in SUPPORTED_EXTENSIONS) return null
        return cache.put("mpv_${id.hashCode()}.$normalizedExtension", text.toByteArray(StandardCharsets.UTF_8))
    }

    fun canBeginCacheWrite(): Boolean = mediaCache.canBeginCacheWrite(MediaCacheCategory.SUBTITLES)

    /**
     * Removes all cached subtitle files.
     */
    fun clearCache() {
        try {
            cache.clear()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear subtitle cache", e)
        }
    }

    private fun guessExtension(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').trimEnd('/')
        return when {
            path.endsWith(".vtt", ignoreCase = true) -> "vtt"
            path.endsWith(".ass", ignoreCase = true) -> "ass"
            path.endsWith(".ssa", ignoreCase = true) -> "ssa"
            path.endsWith(".ttml", ignoreCase = true) -> "ttml"
            path.endsWith(".dfxp", ignoreCase = true) -> "dfxp"
            else -> "srt"
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .take(100) // Limit filename length
    }

    companion object {
        private const val TAG = "SubtitleFileCache"
        private const val SUBTITLE_CACHE_DIR = "subtitles"
        private val SUPPORTED_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "ttml", "dfxp")
    }
}
