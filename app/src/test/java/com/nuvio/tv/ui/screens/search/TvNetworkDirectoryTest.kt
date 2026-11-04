package com.nuvio.tv.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvNetworkDirectoryTest {
    @Test fun findsBrazilianNetworkByNameAndPreservesItsTmdbId() {
        assertTrue(TvNetworkDirectory.search("globo").any { it.id == 60 && it.name == "TV Globo" })
        assertEquals(emptyList<TvNetworkMatch>(), TvNetworkDirectory.search("g"))
        assertTrue(TvNetworkDirectory.search("bbc").size <= 12)
    }
}
