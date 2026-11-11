package com.nuvio.tv.ui.screens.detail

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeroMotionTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trailerTransitionRespectsDisabledMotionAndRestoresTitle() {
        val trailer = mutableStateOf(false)
        val meta = Meta(id = "fixture", type = ContentType.MOVIE, name = "Filme de teste",
            poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
            description = null, releaseInfo = null, imdbRating = null, genres = emptyList(),
            runtime = null, director = emptyList(), cast = emptyList(), videos = emptyList(),
            country = null, awards = null, language = null, links = emptyList())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                HeroContentSection(meta = meta, nextEpisode = null, nextToWatch = null,
                    onPlayClick = {}, isInLibrary = false, onToggleLibrary = {}, onLibraryLongPress = {},
                    isMovieWatched = false, isMovieWatchedPending = false, onToggleMovieWatched = {},
                    isTrailerPlaying = trailer.value, hideLogoDuringTrailer = true)
            }
        }
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText(meta.name).assertIsDisplayed()
        compose.runOnIdle { trailer.value = true }
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText(meta.name).assertDoesNotExist()
        compose.runOnIdle { trailer.value = false }
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText(meta.name).assertIsDisplayed()
    }
}
