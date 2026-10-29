package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.remote.api.TmdbApi
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbServiceCacheTest {
    @Test
    fun `id mapping cache evicts least recently used entries and clears`() {
        val service = TmdbService(mockk<TmdbApi>(), mappingCacheCapacity = 2)
        service.preCacheMapping("tt0000001", 1)
        service.preCacheMapping("tt0000002", 2)

        assertEquals(1, service.cachedTmdbId("tt0000001"))

        service.preCacheMapping("tt0000003", 3)

        assertNull(service.cachedTmdbId("tt0000002"))
        assertEquals(1, service.cachedTmdbId("tt0000001"))
        assertEquals(3, service.cachedTmdbId("tt0000003"))

        service.clearCache()

        assertNull(service.cachedTmdbId("tt0000001"))
        assertNull(service.cachedTmdbId("tt0000003"))
    }
}
