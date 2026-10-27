package com.nuvio.tv.core.storage

import java.util.LinkedHashMap

/** A small synchronized access-order cache whose retained entry count never exceeds [capacity]. */
class BoundedLruCache<K, V : Any>(private val capacity: Int) {
    private val entries = LinkedHashMap<K, V>(capacity.coerceAtLeast(1), 0.75f, true)

    init {
        require(capacity > 0) { "Cache capacity must be positive" }
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        entries[key] = value
        while (entries.size > capacity) {
            val oldest = entries.entries.iterator()
            oldest.next()
            oldest.remove()
        }
    }

    @get:Synchronized
    val size: Int
        get() = entries.size

    @Synchronized
    fun clear() = entries.clear()
}
