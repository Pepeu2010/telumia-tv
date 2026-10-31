package com.nuvio.tv.data.cache

import java.util.LinkedHashMap

/** Small synchronized, access-ordered cache for short-lived derived metadata. */
internal class BoundedTimedLruCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMs: Long,
) {
    private data class Entry<V>(val value: V, val updatedAtMs: Long)

    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)

    init {
        require(maxEntries > 0)
        require(ttlMs >= 0L)
    }

    @Synchronized
    fun get(key: K, nowMs: Long): V? {
        val entry = entries[key] ?: return null
        if (nowMs - entry.updatedAtMs > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V, nowMs: Long) {
        removeExpiredEntries(nowMs)
        entries[key] = Entry(value, nowMs)
        while (entries.size > maxEntries) {
            entries.remove(entries.entries.iterator().next().key)
        }
    }

    @Synchronized
    fun remove(key: K) {
        entries.remove(key)
    }

    private fun removeExpiredEntries(nowMs: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value.updatedAtMs > ttlMs) iterator.remove()
        }
    }
}
