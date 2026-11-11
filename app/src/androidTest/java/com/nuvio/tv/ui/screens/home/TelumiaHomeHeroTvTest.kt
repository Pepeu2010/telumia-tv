package com.nuvio.tv.ui.screens.home

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.core.player.LocalTrailerPlayerPool
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.ui.screens.player.originalTimelineVideo
import android.net.Uri
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.StableList
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelumiaHomeHeroTvTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val movie = HeroPreview(title = "Uma jornada pelo Brasil", logo = null,
        description = "Uma viagem entre cidades e histórias que mudam a vida de uma família.",
        contentTypeText = "Filme", yearText = "2026", runtimeText = "124 min", imdbText = "8.1",
        ageRatingText = "12", languageText = "Português", genres = StableList(listOf("Drama")),
        poster = null, backdrop = null, imageUrl = null)

    @Test fun continueWatchingTrailerHidesChromeAndRemoteRestoresIt() {
        val context = instrumentation.targetContext
        val video = originalTimelineVideo(context, durationSeconds = 15)
        val pool = TrailerPlayerPool(context, forceNative = false)
        val progress = WatchProgress("series-preview", "series", "Série de prévia", null, null, null,
            "episode-preview", 1, 2, "Episódio", 500, 1000, 0)
        val entry = ContinueWatchingItem.InProgress(progress)
        val presentation = buildModernHomePresentation(
            ModernHomePresentationInput(emptyList(), emptyList(), listOf(entry), emptyList(), false, false, true, true, "pt-BR"),
            ModernCarouselRowBuildCache(), context
        )
        val urls = mutableStateMapOf<String, String>()
        val fullscreen = AtomicBoolean(false)
        val resumed = AtomicReference<ContinueWatchingItem>()
        try {
            compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                CompositionLocalProvider(LocalTrailerPlayerPool provides pool) {
                    ModernHomeContent(
                        uiState = HomeUiState(isLoading = false, continueWatchingItems = listOf(entry),
                            focusedPosterBackdropTrailerEnabled = true, focusedPosterBackdropExpandEnabled = false,
                            focusedPosterBackdropExpandDelaySeconds = 0),
                        modernPresentation = presentation, focusState = HomeScreenFocusState(),
                        trailerPreviewUrls = urls,
                        onNavigateToDetail = { _, _, _ -> }, onContinueWatchingClick = { resumed.set(it) },
                        onRequestTrailerPreview = { id, _, _, _ -> urls[id] = Uri.fromFile(video).toString() },
                        onLoadMoreCatalog = { _, _, _ -> }, onRemoveContinueWatching = { _, _, _, _ -> },
                        onSaveFocusState = { _, _, _, _, _, _, _, _ -> },
                        onFullscreenTrailerChanged = { fullscreen.set(it) }
                    )
                }
            } }
            compose.waitUntil(timeoutMillis = 20_000) { fullscreen.get() }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(timeoutMillis = 5_000) { !fullscreen.get() }
            assertEquals(null, resumed.get())
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(timeoutMillis = 5_000) { resumed.get() != null }
            assertEquals(entry, resumed.get())
        } finally {
            compose.runOnIdle { urls.clear() }
            instrumentation.runOnMainSync { pool.release() }
            video.delete()
        }
    }

    @Test fun continueWatchingFocusRequestsParentTrailerAndClickStillResumes() {
        val progress = WatchProgress("series-fixture", "series", "Série de teste", null, null, null,
            "episode-fixture", 1, 2, "Episódio", 500, 1000, 0)
        val entry = ContinueWatchingItem.InProgress(progress, releaseInfo = "2024")
        val input = ModernHomePresentationInput(
            homeRows = emptyList(), catalogRows = emptyList(), continueWatchingItems = listOf(entry),
            upcomingItems = emptyList(), useLandscapePosters = false, showCatalogTypeSuffix = false,
            showFullReleaseDate = true, showImdbRatings = true, localeTag = "pt-BR"
        )
        val presentation = buildModernHomePresentation(input, ModernCarouselRowBuildCache(), instrumentation.targetContext)
        val requested = AtomicReference<List<String?>>()
        val resumed = AtomicReference<ContinueWatchingItem>()
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            ModernHomeContent(
                uiState = HomeUiState(isLoading = false, continueWatchingItems = listOf(entry),
                    focusedPosterBackdropTrailerEnabled = true, focusedPosterBackdropExpandEnabled = false,
                    focusedPosterBackdropExpandDelaySeconds = 0),
                modernPresentation = presentation, focusState = HomeScreenFocusState(),
                onNavigateToDetail = { _, _, _ -> }, onContinueWatchingClick = { resumed.set(it) },
                onRequestTrailerPreview = { id, title, year, type -> requested.set(listOf(id, title, year, type)) },
                onLoadMoreCatalog = { _, _, _ -> }, onRemoveContinueWatching = { _, _, _, _ -> },
                onSaveFocusState = { _, _, _, _, _, _, _, _ -> }
            )
        } }
        compose.waitUntil(timeoutMillis = 15_000) { requested.get() != null }
        assertEquals(listOf("series-fixture", "Série de teste", "2024", "series"), requested.get())
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(timeoutMillis = 5_000) { resumed.get() != null }
        assertEquals(entry, resumed.get())
    }

    @Test fun dpadSelectionUpdatesTheHeroWithoutRetainingAnotherTitlesMetadata() {
        val second = movie.copy(title = "Histórias de uma cidade", contentTypeText = "Série", isSeries = true,
            yearText = "2025", imdbText = "7.4", description = "Histórias que se encontram numa cidade.")
        val selected = mutableStateOf(movie)
        val firstFocus = FocusRequester()
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
                HeroTitleBlock(previewProvider = { selected.value }, portraitMode = true, showImdbRatings = true,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 32.dp, end = 32.dp, top = 32.dp).fillMaxWidth(0.52f))
                Row(Modifier.align(Alignment.BottomStart).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { selected.value = movie }, modifier = Modifier.focusRequester(firstFocus)
                        .onFocusChanged { if (it.isFocused) selected.value = movie }) { Text("01") }
                    Button(onClick = { selected.value = second }, modifier = Modifier
                        .onFocusChanged { if (it.isFocused) selected.value = second }) { Text("02") }
                }
                LaunchedEffect(Unit) { firstFocus.requestFocusAfterFrames() }
            }
        } }
        compose.onNodeWithText(movie.title).assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText(second.title).assertIsDisplayed()
        compose.onNodeWithText("7.4").assertIsDisplayed()
        compose.onNodeWithText("8.1").assertDoesNotExist()
        compose.onNodeWithText("02").assertIsFocused()
        val directory = instrumentation.targetContext.getExternalFilesDir(null)!!
        File(directory, "telumia-home-hero-tv.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun unknownRatingsAndEmptyMetadataCreateNoInventedContent() {
        val preview = mutableStateOf(movie.copy(imdbText = "NaN", yearText = null, runtimeText = null,
            ageRatingText = null, languageText = null, description = null, contentTypeText = null,
            genres = StableList()))
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            HeroTitleBlock(previewProvider = { preview.value }, portraitMode = true, showImdbRatings = true,
                modifier = Modifier.width(320.dp))
        } }
        compose.onNodeWithText(movie.title).assertIsDisplayed()
        compose.onNodeWithText("IMDb", substring = true).assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.runOnIdle { preview.value = preview.value.copy(imdbText = "20.0") }
        compose.onNodeWithText("IMDb", substring = true).assertDoesNotExist()
    }

    @Test fun fullscreenPreviewHidesMetadataFromAccessibilityAndRestoresItAfterwards() {
        val trailer = mutableStateOf(false)
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            HeroTitleBlock(previewProvider = { movie }, portraitMode = true, showImdbRatings = true,
                trailerPlaying = { trailer.value }, modifier = Modifier.width(400.dp))
        } }
        compose.onNodeWithText("2026", substring = true).assertIsDisplayed()
        compose.runOnIdle { trailer.value = true }
        compose.onNodeWithText("2026", substring = true).assertDoesNotExist()
        compose.onNodeWithText(movie.title).assertDoesNotExist()
        compose.runOnIdle { trailer.value = false }
        compose.onNodeWithText("2026", substring = true).assertIsDisplayed()
        compose.onNodeWithText(movie.title).assertIsDisplayed()
    }

    @Test fun dpadInputDismissesImmersiveTrailerAndKeepsFocusUsable() {
        val trailerPlaying = mutableStateOf(true)
        val firstFocus = FocusRequester()
        var dismissals = 0
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            Box(Modifier.fillMaxSize().exitImmersiveTrailerOnKey(
                isTrailerPlaying = { trailerPlaying.value },
                onExit = { trailerPlaying.value = false; dismissals++ }
            )) {
                Button(onClick = {}, modifier = Modifier.focusRequester(firstFocus)) { Text("Home preview") }
            }
            LaunchedEffect(Unit) { firstFocus.requestFocusAfterFrames() }
        } }
        compose.onNodeWithText("Home preview").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.runOnIdle {
            assertFalse(trailerPlaying.value)
            assertEquals(1, dismissals)
        }
        compose.onNodeWithText("Home preview").assertIsFocused()
    }
}
