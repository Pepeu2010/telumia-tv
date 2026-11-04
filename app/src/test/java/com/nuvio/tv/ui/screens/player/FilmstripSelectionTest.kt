package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FilmstripSelectionTest {
    @Test fun keepsPreviousAndNextFramesDespiteCrowdedCache() {
        val frames = listOf(7500L, 8000L, 8500L, 9000L, 11000L, 12000L)
        assertEquals(listOf(8500L, 9000L, 11000L, 12000L),
            selectFilmstripNeighbors(10000L, frames, { it }))
    }
    @Test fun fillsRemainingSlotsNearAnEdgeAndRemovesDuplicates() {
        assertEquals(listOf(1500L, 2000L, 2500L, 3000L),
            selectFilmstripNeighbors(1000L, listOf(1000L, 1500L, 1500L, 2000L, 2500L, 3000L), { it }))
    }
}
