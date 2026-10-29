package com.nuvio.tv.core.storage

internal data class BoundedPreferenceCacheEntry(
    val key: String,
    val storedBytes: Long,
    val savedAtMs: Long,
)

/** Estimates a DataStore preference plus its shadow-copy file, with room for protobuf framing. */
internal fun estimateShadowedPreferenceCacheBytes(vararg entries: Pair<String, String>): Long {
    var primaryBytes = 0L
    entries.forEach { (key, value) ->
        val entryBytes = key.toByteArray(Charsets.UTF_8).size.toLong() +
            value.toByteArray(Charsets.UTF_8).size.toLong() + 48L
        primaryBytes = if (primaryBytes > Long.MAX_VALUE - entryBytes) Long.MAX_VALUE else primaryBytes + entryBytes
    }
    return if (primaryBytes > Long.MAX_VALUE / 2L) Long.MAX_VALUE else primaryBytes * 2L
}

/** Keeps the newest cache entry while evicting older entries to satisfy both bounds. */
internal object BoundedPreferenceCachePolicy {
    fun retain(
        entries: List<BoundedPreferenceCacheEntry>,
        maxEntries: Int,
        maxBytes: Long,
        protectedKey: String? = null,
    ): List<BoundedPreferenceCacheEntry> {
        require(maxEntries >= 0 && maxBytes >= 0)
        val candidateIndex = protectedKey?.let { key -> entries.indexOfLast { it.key == key } } ?: -1
        val candidate = entries.getOrNull(candidateIndex)
        val beforeCandidate = entries.filterIndexed { index, _ -> index != candidateIndex }
        val fallback = beforeCandidate.associateBy { it.key }.values.toList()
        if (candidate != null && (maxEntries == 0 || candidate.storedBytes > maxBytes)) return fallback

        val retained = beforeCandidate
            .asSequence()
            .filterNot { it.key == protectedKey }
            .associateBy { it.key }
            .values
            .toMutableList()
        if (candidate != null) retained += candidate

        while (retained.size > maxEntries || retained.sumOfSaturated { it.storedBytes } > maxBytes) {
            val oldest = retained.asSequence()
                .filterNot { it.key == protectedKey }
                .minWithOrNull(compareBy<BoundedPreferenceCacheEntry> { it.savedAtMs }.thenBy { it.key })
                ?: break
            retained.remove(oldest)
        }
        return retained
    }

    private inline fun <T> Iterable<T>.sumOfSaturated(selector: (T) -> Long): Long {
        var total = 0L
        for (item in this) {
            val bytes = selector(item).coerceAtLeast(0L)
            total = if (total > Long.MAX_VALUE - bytes) Long.MAX_VALUE else total + bytes
        }
        return total
    }
}
