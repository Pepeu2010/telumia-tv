package com.nuvio.tv.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TtlLruCacheTest {
    @Test
    fun `least recently used item is evicted after a cache hit`() {
        val now = 1_000L
        val cache = TtlLruCache<String, String>(capacity = 2, ttlMillis = 100, nowMillis = { now })
        cache.put("a", "first")
        cache.put("b", "second")

        assertEquals("first", cache.get("a"))
        cache.put("c", "third")

        assertNull(cache.get("b"))
        assertEquals("first", cache.get("a"))
        assertEquals("third", cache.get("c"))
    }

    @Test
    fun `expired item is removed when it is read`() {
        var now = 1_000L
        val cache = TtlLruCache<String, String>(capacity = 2, ttlMillis = 50, nowMillis = { now })
        cache.put("key", "value")

        now = 1_051L

        assertNull(cache.get("key"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `empty collections remain cacheable values`() {
        val cache = TtlLruCache<String, List<String>>(capacity = 2, ttlMillis = 100) { 1_000L }
        cache.put("empty", emptyList())

        assertEquals(emptyList<String>(), cache.get("empty"))
        assertEquals(1, cache.size)
    }

    @Test
    fun `explicit removal deletes the value`() {
        val cache = TtlLruCache<String, String>(capacity = 2, ttlMillis = 100)
        cache.put("key", "value")

        cache.remove("key")

        assertNull(cache.get("key"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `expired cleanup preserves a concurrent replacement`() {
        var now = 1_000L
        var onReadClock: (() -> Unit)? = null
        val cache = TtlLruCache<String, String>(capacity = 2, ttlMillis = 10) {
            onReadClock?.also { onReadClock = null }?.invoke()
            now
        }
        cache.put("key", "old")
        now = 1_011L
        onReadClock = { cache.put("key", "new") }

        assertNull(cache.get("key"))
        assertEquals("new", cache.get("key"))
        assertEquals(1, cache.size)
    }

    @Test
    fun `capacity and time to live must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            TtlLruCache<String, String>(capacity = 0, ttlMillis = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtlLruCache<String, String>(capacity = 1, ttlMillis = 0)
        }
    }
}
