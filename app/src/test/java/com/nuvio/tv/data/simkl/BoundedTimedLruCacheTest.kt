package com.nuvio.tv.data.simkl

import com.nuvio.tv.data.cache.BoundedTimedLruCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedTimedLruCacheTest {
    @Test
    fun `evicts least recently used entry at capacity`() {
        val cache = BoundedTimedLruCache<String, String>(maxEntries = 2, ttlMs = 1_000)
        cache.put("a", "A", nowMs = 0)
        cache.put("b", "B", nowMs = 0)

        assertEquals("A", cache.get("a", nowMs = 1))
        cache.put("c", "C", nowMs = 2)

        assertNull(cache.get("b", nowMs = 3))
        assertEquals("A", cache.get("a", nowMs = 3))
        assertEquals("C", cache.get("c", nowMs = 3))
    }

    @Test
    fun `expired entries miss and are removed`() {
        val cache = BoundedTimedLruCache<String, String>(maxEntries = 2, ttlMs = 10)
        cache.put("a", "A", nowMs = 100)

        assertEquals("A", cache.get("a", nowMs = 110))
        assertNull(cache.get("a", nowMs = 111))
    }

    @Test
    fun `inserting removes expired entries before applying capacity`() {
        val cache = BoundedTimedLruCache<String, String>(maxEntries = 2, ttlMs = 10)
        cache.put("a", "A", nowMs = 0)
        cache.put("b", "B", nowMs = 15)
        cache.put("c", "C", nowMs = 16)

        assertNull(cache.get("a", nowMs = 16))
        assertEquals("B", cache.get("b", nowMs = 16))
        assertEquals("C", cache.get("c", nowMs = 16))
    }

    @Test
    fun `explicit removal supports force refresh`() {
        val cache = BoundedTimedLruCache<String, String>(maxEntries = 1, ttlMs = 10)
        cache.put("a", "old", nowMs = 1)
        cache.remove("a")

        assertNull(cache.get("a", nowMs = 2))
    }

    @Test
    fun `entry cap remains fixed as distinct keys arrive`() {
        val maximum = RELATED_CACHE_MAX_ENTRIES
        val cache = BoundedTimedLruCache<Int, String>(maxEntries = maximum, ttlMs = 1_000)
        repeat(maximum * 4) { cache.put(it, "item-$it", nowMs = it.toLong()) }

        assertNull(cache.get(0, nowMs = 300))
        assertTrue(((maximum * 3)..(maximum * 4 - 1)).all { cache.get(it, nowMs = 300) != null })
    }
}
