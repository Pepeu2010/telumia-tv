package com.nuvio.tv.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedLruCacheTest {
    @Test
    fun `least recently used entry is evicted after a cache hit`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2

        assertEquals(1, cache["a"])
        cache["c"] = 3

        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
        assertEquals(2, cache.size)
    }

    @Test
    fun `concurrent inserts never exceed configured capacity`() {
        val cache = BoundedLruCache<Int, Int>(16)
        val workers = (0 until 4).map { worker ->
            Thread {
                repeat(500) { item -> cache[worker * 500 + item] = item }
            }.apply(Thread::start)
        }

        workers.forEach { worker ->
            worker.join(5_000)
            assertFalse("cache writer should finish", worker.isAlive)
        }

        assertEquals(16, cache.size)
        assertTrue((0 until 2_000).count { cache[it] != null } <= 16)
    }

    @Test
    fun `conditional removal preserves a newer replacement`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache["key"] = 1
        cache["key"] = 2

        assertFalse(cache.remove("key", 1))
        assertEquals(2, cache["key"])
        assertTrue(cache.remove("key", 2))
        assertNull(cache["key"])
    }

    @Test
    fun `clearing a cache removes every retained value`() {
        val cache = BoundedLruCache<String, Int>(3)
        cache["a"] = 1
        cache["b"] = 2

        cache.clear()

        assertEquals(0, cache.size)
        assertNull(cache["a"])
    }
}
