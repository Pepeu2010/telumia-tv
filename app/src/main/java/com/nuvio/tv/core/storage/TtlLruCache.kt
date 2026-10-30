package com.nuvio.tv.core.storage

/** A bounded access-order cache that expires entries after a fixed interval. */
internal class TtlLruCache<K, V : Any>(
    capacity: Int,
    private val ttlMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(val value: V, val storedAtMillis: Long)

    private val entries = BoundedLruCache<K, Entry<V>>(capacity)

    init {
        require(ttlMillis > 0L) { "Cache time to live must be positive" }
    }

    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (nowMillis() - entry.storedAtMillis <= ttlMillis) return entry.value

        // Conditional removal leaves a value written by a concurrent refresh untouched.
        entries.remove(key, entry)
        return null
    }

    fun put(key: K, value: V) {
        entries[key] = Entry(value, nowMillis())
    }

    fun remove(key: K) {
        entries[key]?.let { entries.remove(key, it) }
    }

    val size: Int
        get() = entries.size
}
