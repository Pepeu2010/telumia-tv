package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroSectionLayoutTest {
    @Test
    fun `movie hero uses compact layout at common 1080p TV density`() {
        assertTrue(shouldUseCompactMovieHero(isMovie = true, viewportHeightDp = 540))
    }

    @Test
    fun `movie hero switches to expanded layout at taller viewports`() {
        assertFalse(shouldUseCompactMovieHero(isMovie = true, viewportHeightDp = 600))
        assertFalse(shouldUseCompactMovieHero(isMovie = true, viewportHeightDp = 720))
    }

    @Test
    fun `series hero keeps its series-specific layout at every viewport`() {
        assertFalse(shouldUseCompactMovieHero(isMovie = false, viewportHeightDp = 360))
        assertFalse(shouldUseCompactMovieHero(isMovie = false, viewportHeightDp = 540))
    }
}
