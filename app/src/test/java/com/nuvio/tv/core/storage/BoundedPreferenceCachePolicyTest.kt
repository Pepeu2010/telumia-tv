package com.nuvio.tv.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedPreferenceCachePolicyTest {
    @Test
    fun `evicts oldest entries until entry and byte limits both hold`() {
        val retained = BoundedPreferenceCachePolicy.retain(
            entries = listOf(
                BoundedPreferenceCacheEntry("old", storedBytes = 4, savedAtMs = 1),
                BoundedPreferenceCacheEntry("middle", storedBytes = 4, savedAtMs = 2),
                BoundedPreferenceCacheEntry("new", storedBytes = 4, savedAtMs = 3),
            ),
            maxEntries = 2,
            maxBytes = 8,
            protectedKey = "new",
        )

        assertEquals(setOf("middle", "new"), retained.map { it.key }.toSet())
    }

    @Test
    fun `oversized new entry is rejected without evicting existing hits`() {
        val retained = BoundedPreferenceCachePolicy.retain(
            entries = listOf(
                BoundedPreferenceCacheEntry("existing", storedBytes = 5, savedAtMs = 1),
                BoundedPreferenceCacheEntry("new", storedBytes = 9, savedAtMs = 2),
            ),
            maxEntries = 8,
            maxBytes = 8,
            protectedKey = "new",
        )

        assertEquals(setOf("existing"), retained.map { it.key }.toSet())
    }

    @Test
    fun `legacy entries without timestamps are evicted before newly saved values`() {
        val retained = BoundedPreferenceCachePolicy.retain(
            entries = listOf(
                BoundedPreferenceCacheEntry("legacy", storedBytes = 3, savedAtMs = 0),
                BoundedPreferenceCacheEntry("new", storedBytes = 3, savedAtMs = 10),
            ),
            maxEntries = 1,
            maxBytes = 10,
            protectedKey = "new",
        )

        assertEquals(setOf("new"), retained.map { it.key }.toSet())
    }

    @Test
    fun `replacement counts the latest value once while keeping its existing key`() {
        val old = BoundedPreferenceCacheEntry("same", storedBytes = 6, savedAtMs = 1)
        val replacement = BoundedPreferenceCacheEntry("same", storedBytes = 8, savedAtMs = 3)
        val retained = BoundedPreferenceCachePolicy.retain(
            entries = listOf(old, BoundedPreferenceCacheEntry("other", 4, 2), replacement),
            maxEntries = 2,
            maxBytes = 12,
            protectedKey = "same",
        )

        assertEquals(1, retained.count { it.key == "same" })
        assertEquals(replacement, retained.single { it.key == "same" })
        assertEquals(setOf("same", "other"), retained.map { it.key }.toSet())
    }

    @Test
    fun `oversized replacement preserves its previous readable value`() {
        val old = BoundedPreferenceCacheEntry("same", storedBytes = 4, savedAtMs = 1)
        val retained = BoundedPreferenceCachePolicy.retain(
            entries = listOf(old, BoundedPreferenceCacheEntry("other", 3, 2), BoundedPreferenceCacheEntry("same", 9, 3)),
            maxEntries = 4,
            maxBytes = 8,
            protectedKey = "same",
        )

        assertEquals(setOf("same", "other"), retained.map { it.key }.toSet())
        assertEquals(old, retained.single { it.key == "same" })
    }
}
