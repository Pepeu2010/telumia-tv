package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeroPlaybackHintTvTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun meta(type: ContentType) = Meta(id = "fixture:hint", type = type, name = "Titulo",
        poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = "Sinopse curta.", releaseInfo = "2026", imdbRating = 8.1f,
        genres = listOf("Drama"), runtime = "47 min", director = listOf("Diretor"), cast = emptyList(),
        videos = emptyList(), country = null, awards = null, language = null, links = emptyList())

    private fun render(type: ContentType, playEnabled: Boolean) {
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
                    HeroContentSection(meta = meta(type), nextEpisode = null,
                        nextToWatch = NextToWatch(null, false, null, null, null, "Assistir"),
                        onPlayClick = {}, isPlayEnabled = playEnabled, isInLibrary = false,
                        onToggleLibrary = {}, onLibraryLongPress = {}, isMovieWatched = false,
                        isMovieWatchedPending = false, onToggleMovieWatched = {})
                }
            }
        }
    }

    private val hint get() = context.getString(R.string.playback_unavailable_hint)

    @Test fun movieWithoutPlaybackExplainsNextStep() {
        render(ContentType.MOVIE, playEnabled = false)
        compose.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test fun seriesWithoutPlaybackExplainsNextStep() {
        render(ContentType.SERIES, playEnabled = false)
        compose.onNodeWithText(context.getString(R.string.playback_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(hint).assertIsDisplayed()
    }
}
