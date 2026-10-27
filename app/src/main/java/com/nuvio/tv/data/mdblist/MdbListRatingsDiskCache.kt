package com.nuvio.tv.data.mdblist

import android.content.Context
import com.nuvio.tv.core.storage.MediaCacheCategory
import com.nuvio.tv.core.storage.tvMediaCache
import com.nuvio.tv.domain.model.MDBListRatings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

internal class MdbListRatingsDiskCache(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    private val quotaBytes: () -> Long = { tvMediaCache(context).coilQuota(MediaCacheCategory.METADATA) },
    private val writesEnabled: () -> Boolean = { tvMediaCache(context).writesEnabled },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private data class Entry(val ratings: MDBListRatings, val storedAtMs: Long)
    private data class Snapshot(val entries: Map<String, Entry>, val revision: Long)

    private val mutex = Mutex()
    private val stateLock = Any()
    private var entries = mutableMapOf<String, Entry>()
    private var loaded = false
    private var dirty = false
    private var revision = 0L
    private var writeJob: Job? = null

    private fun cacheFile(): File = File(context.cacheDir, "metadata/mdblist/ratings_v1.json")
    private fun legacyFile(): File = File(context.filesDir, "mdblist_cache/ratings_v1.json")

    suspend fun load() = withContext(ioDispatcher) {
        if (synchronized(stateLock) { loaded }) return@withContext
        mutex.withLock {
            if (synchronized(stateLock) { loaded }) return@withLock
            val currentTime = now()
            val cutoff = currentTime - TTL_MS
            val currentFile = cacheFile()
            val oldFile = legacyFile()
            val source = when {
                currentFile.isFile -> currentFile
                oldFile.isFile -> oldFile
                else -> null
            }
            val parsed = source?.let { readEntries(it, cutoff) }
            synchronized(stateLock) {
                entries = parsed.orEmpty().toMutableMap()
                loaded = true
                revision++
            }
            if (source == oldFile || (source == currentFile && parsed != null)) {
                // Ratings are regenerable metadata. Keep the old payload only until it has been read.
                oldFile.delete()
                oldFile.parentFile?.delete()
            }
            if (source == currentFile && parsed.isNullOrEmpty()) currentFile.delete()
            if (source == oldFile && !parsed.isNullOrEmpty()) scheduleSave()
        }
    }

    suspend fun get(key: String): MDBListRatings? {
        if (!synchronized(stateLock) { loaded }) load()
        synchronized(stateLock) {
            val entry = entries[key] ?: return null
            if (now() - entry.storedAtMs > TTL_MS) {
                entries.remove(key)
                revision++
                scheduleSave()
                return null
            }
            return entry.ratings
        }
    }

    fun put(key: String, ratings: MDBListRatings) {
        synchronized(stateLock) {
            entries[key] = Entry(ratings, now())
            if (entries.size > MAX_MEMORY_ENTRIES) {
                val retained = entries.entries
                    .sortedByDescending { it.value.storedAtMs }
                    .take(MAX_MEMORY_ENTRIES)
                    .associateTo(mutableMapOf()) { it.key to it.value }
                entries = retained
            }
            revision++
        }
        scheduleSave()
    }

    private fun scheduleSave() {
        synchronized(stateLock) {
            dirty = true
            writeJob?.cancel()
            writeJob = scope.launch {
                delay(SAVE_DEBOUNCE_MS)
                persist()
            }
        }
    }

    private suspend fun persist() = withContext(ioDispatcher) {
        var shouldRetry = false
        mutex.withLock {
            if (!writesEnabled()) return@withLock
            val quota = quotaBytes().coerceAtMost(MAX_CACHE_FILE_BYTES).coerceAtLeast(0L)
            if (quota < EMPTY_JSON_BYTES) return@withLock
            val snapshot = synchronized(stateLock) {
                Snapshot(entries.toMap(), revision)
            }
            val encoded = encodeWithinQuota(snapshot.entries, quota, now() - TTL_MS)
            val target = cacheFile()
            if (encoded == null) {
                target.delete()
                legacyFile().delete()
                legacyFile().parentFile?.delete()
                synchronized(stateLock) {
                    if (revision == snapshot.revision) dirty = false
                    else shouldRetry = dirty
                }
                return@withLock
            }
            val parent = target.parentFile ?: return@withLock
            if (!parent.exists() && !parent.mkdirs()) return@withLock
            val temporary = File(parent, "${target.name}.tmp")
            try {
                temporary.writeBytes(encoded)
                if (temporary.length() > quota || temporary.length() > MAX_CACHE_FILE_BYTES) return@withLock
                if (!temporary.renameTo(target)) {
                    // Same-directory copy remains a last-resort fallback for filesystems without replace rename.
                    temporary.copyTo(target, overwrite = true)
                }
                legacyFile().delete()
                legacyFile().parentFile?.delete()
                synchronized(stateLock) {
                    if (revision == snapshot.revision) dirty = false
                    else shouldRetry = dirty
                }
            } catch (_: Exception) {
                // The cache is disposable; reads and the in-memory result remain available.
            } finally {
                temporary.delete()
            }
        }
        if (shouldRetry) scheduleSave()
    }

    private fun readEntries(file: File, cutoff: Long): Map<String, Entry>? {
        if (file.length() !in 1L..MAX_CACHE_FILE_BYTES) return null
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val parsed = mutableMapOf<String, Entry>()
            val keys = json.keys()
            while (keys.hasNext() && parsed.size < MAX_MEMORY_ENTRIES) {
                val key = keys.next()
                if (key.isBlank() || key.length > MAX_KEY_LENGTH) continue
                val obj = json.optJSONObject(key) ?: continue
                val storedAt = obj.optLong("t", 0L)
                if (storedAt < cutoff) continue
                parsed[key] = Entry(parseRatings(obj), storedAt)
            }
            parsed
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeWithinQuota(source: Map<String, Entry>, quota: Long, cutoff: Long): ByteArray? {
        val json = JSONObject()
        var bytesUsed = EMPTY_JSON_BYTES
        var entriesUsed = 0
        source.entries
            .asSequence()
            .filter { it.value.storedAtMs >= cutoff }
            .sortedByDescending { it.value.storedAtMs }
            .forEach { (key, entry) ->
                if (key.isBlank() || key.length > MAX_KEY_LENGTH) return@forEach
                val oneEntry = JSONObject().put(key, serializeEntry(entry)).toString()
                val entryBytes = oneEntry.toByteArray(StandardCharsets.UTF_8).size - EMPTY_JSON_BYTES.toInt()
                val commaBytes = if (entriesUsed == 0) 0 else 1
                val nextSize = bytesUsed + entryBytes + commaBytes
                if (entryBytes > 0 && nextSize <= quota) {
                    json.put(key, serializeEntry(entry))
                    bytesUsed = nextSize
                    entriesUsed++
                }
            }
        if (entriesUsed == 0) return null
        val encoded = json.toString().toByteArray(StandardCharsets.UTF_8)
        return encoded.takeIf { it.size.toLong() <= quota && it.size.toLong() <= MAX_CACHE_FILE_BYTES }
    }

    private fun serializeEntry(entry: Entry): JSONObject = JSONObject().apply {
        put("t", entry.storedAtMs)
        entry.ratings.trakt?.let { put("trakt", it) }
        entry.ratings.imdb?.let { put("imdb", it) }
        entry.ratings.tmdb?.let { put("tmdb", it) }
        entry.ratings.letterboxd?.let { put("letterboxd", it) }
        entry.ratings.tomatoes?.let { put("tomatoes", it) }
        entry.ratings.audience?.let { put("audience", it) }
        entry.ratings.metacritic?.let { put("metacritic", it) }
        entry.ratings.mal?.let { put("mal", it) }
        if (entry.ratings.tomatoesCertified) put("tc", true)
        if (entry.ratings.audienceCertified) put("ac", true)
    }

    private fun parseRatings(obj: JSONObject): MDBListRatings = MDBListRatings(
        trakt = obj.optDoubleOrNull("trakt"),
        imdb = obj.optDoubleOrNull("imdb"),
        tmdb = obj.optDoubleOrNull("tmdb"),
        letterboxd = obj.optDoubleOrNull("letterboxd"),
        tomatoes = obj.optDoubleOrNull("tomatoes"),
        audience = obj.optDoubleOrNull("audience"),
        metacritic = obj.optDoubleOrNull("metacritic"),
        mal = obj.optDoubleOrNull("mal"),
        tomatoesCertified = obj.optBoolean("tc", false),
        audienceCertified = obj.optBoolean("ac", false)
    )

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key)) optDouble(key).takeIf { it.isFinite() } else null

    companion object {
        private const val TTL_MS = 12L * 60L * 60L * 1000L
        private const val SAVE_DEBOUNCE_MS = 2_000L
        private const val MAX_KEY_LENGTH = 512
        private const val MAX_MEMORY_ENTRIES = 25_000
        internal const val MAX_CACHE_FILE_BYTES = 8L * 1024L * 1024L
        private const val EMPTY_JSON_BYTES = 2L
    }
}
