package com.nuvio.tv.ui.screens.home

import android.graphics.Bitmap
import android.view.KeyEvent
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.StableList
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class TelumiaHomeHeroTvTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val movie = HeroPreview(title = "Uma jornada pelo Brasil", logo = null,
        description = "Uma viagem entre cidades e histórias que mudam a vida de uma família.",
        contentTypeText = "Filme", yearText = "2026", runtimeText = "124 min", imdbText = "8.1",
        ageRatingText = "12", languageText = "Português", genres = StableList(listOf("Drama")),
        poster = null, backdrop = null, imageUrl = null)

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

    @Test fun dpadCanEnterActivateAndLeaveThePrimaryAction() {
        val rowFocus = FocusRequester()
        var activations = 0
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            Column(Modifier.width(500.dp)) {
                HeroTitleContent(
                    previewProvider = { movie },
                    portraitMode = false,
                    showImdbRatings = true,
                    onPrimaryAction = { activations++ },
                )
                Button(onClick = {}, modifier = Modifier.padding(top = 24.dp)
                    .focusRequester(rowFocus).testTag("content_row")) { Text("01") }
            }
            LaunchedEffect(Unit) { rowFocus.requestFocusAfterFrames() }
        } }

        val actionLabel = instrumentation.targetContext.getString(R.string.cw_action_go_to_details)
        compose.onNodeWithTag("content_row").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithText(actionLabel).assertIsFocused().assertIsDisplayed()
        val action = compose.onNodeWithText(actionLabel).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText(actionLabel, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Focused CTA label should stay centered horizontally", abs(label.center.x - action.center.x) < 2f)
        assertTrue("Focused CTA label should stay centered vertically: label=$label action=$action", abs(label.center.y - action.center.y) < 2f)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals("D-pad center should activate the CTA once", 1, activations) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("content_row").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithText(actionLabel).assertIsFocused()
    }

    @Test fun primaryActionIsAnchoredLeftAndItsLabelStaysCentered() {
        val actionLabel = instrumentation.targetContext.getString(R.string.cw_action_go_to_details)
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            Box(Modifier.width(500.dp).height(500.dp).testTag("hero_action_parent")) {
                HeroTitleContent(
                    previewProvider = { movie },
                    portraitMode = false,
                    showImdbRatings = true,
                    onPrimaryAction = {},
                )
            }
        } }

        val panel = compose.onNodeWithTag("hero_action_parent").fetchSemanticsNode().boundsInRoot
        val action = compose.onNode(hasClickAction()).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText(actionLabel, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        compose.onNodeWithText(actionLabel).assertIsDisplayed()
        val actionInset = action.left - panel.left
        assertTrue("CTA should remain compact instead of spanning the panel", action.width < panel.width * 0.6f)
        assertTrue("CTA should sit near the panel's left edge", actionInset >= 0f && actionInset < action.width / 2f)
        assertTrue("CTA label should be centered inside its button", abs(label.center.x - action.center.x) < 2f)
        assertActionContained(panel, action, label)
        compose.waitForIdle()
        val directory = instrumentation.targetContext.getExternalFilesDir(null)!!
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        listOf(
            File(directory, "telumia-home-hero-cta-tv.png"),
            File(instrumentation.targetContext.filesDir, "telumia-home-hero-cta-tv.png")
        ).forEach { file ->
            file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        screenshot.recycle()
    }

    @Test fun dpadSkipsTheHiddenPrimaryActionDuringFullscreenTrailer() {
        val rowFocus = FocusRequester()
        val trailer = mutableStateOf(true)
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            Column(Modifier.width(500.dp)) {
                HeroTitleContent(
                    previewProvider = { movie }, portraitMode = false, showImdbRatings = true,
                    onPrimaryAction = {}, trailerPlaying = { trailer.value },
                )
                Button(onClick = {}, modifier = Modifier.padding(top = 24.dp)
                    .focusRequester(rowFocus).testTag("content_row")) { Text("01") }
            }
            LaunchedEffect(Unit) { rowFocus.requestFocusAfterFrames() }
        } }

        val actionLabel = instrumentation.targetContext.getString(R.string.cw_action_go_to_details)
        compose.onNodeWithTag("content_row").assertIsFocused()
        compose.onNodeWithText(actionLabel).assertDoesNotExist()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("content_row").assertIsFocused()
        compose.runOnIdle { trailer.value = false }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithText(actionLabel).assertIsFocused().assertIsDisplayed()
    }

    @Test fun trailerStartingWhilePrimaryActionIsFocusedReleasesFocusAndKeepsDpadUsable() {
        val rowFocus = FocusRequester()
        val trailer = mutableStateOf(false)
        var heroHasFocus = false
        var activations = 0
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            Column(Modifier.width(500.dp)) {
                Box(Modifier.onFocusChanged { heroHasFocus = it.hasFocus }) {
                    HeroTitleContent(
                        previewProvider = { movie }, portraitMode = false, showImdbRatings = true,
                        onPrimaryAction = { activations++ }, trailerPlaying = { trailer.value },
                    )
                }
                Button(onClick = {}, modifier = Modifier.padding(top = 24.dp)
                    .focusRequester(rowFocus).testTag("content_row")) { Text("01") }
            }
            LaunchedEffect(Unit) { rowFocus.requestFocusAfterFrames() }
        } }

        val actionLabel = instrumentation.targetContext.getString(R.string.cw_action_go_to_details)
        compose.onNodeWithTag("content_row").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithText(actionLabel).assertIsFocused()
        compose.runOnIdle {
            assertTrue("Focus observer must see the focused CTA before hiding it", heroHasFocus)
            trailer.value = true
        }
        compose.onNodeWithText(actionLabel).assertDoesNotExist()
        compose.runOnIdle { assertFalse("Starting the trailer must release focus from the invisible CTA", heroHasFocus) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("content_row").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("content_row").assertIsFocused()
        compose.runOnIdle {
            assertEquals("The hidden CTA must not be activated", 0, activations)
            trailer.value = false
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithText(actionLabel).assertIsFocused().assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals("The restored CTA must remain usable", 1, activations) }
    }

    @Test fun primaryActionAlignmentAlsoFitsACompactMobileViewport() {
        val actionLabel = instrumentation.targetContext.getString(R.string.cw_action_go_to_details)
        val mobileConfiguration = Configuration(instrumentation.targetContext.resources.configuration).apply {
            screenWidthDp = 390
            screenHeightDp = 844
        }
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides mobileConfiguration) {
                NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                    Box(Modifier.width(360.dp).height(760.dp).testTag("mobile_hero_action_parent")) {
                        HeroTitleContent(
                            previewProvider = { movie },
                            portraitMode = true,
                            showImdbRatings = true,
                            onPrimaryAction = {},
                        )
                    }
                }
            }
        }

        val panel = compose.onNodeWithTag("mobile_hero_action_parent").fetchSemanticsNode().boundsInRoot
        val action = compose.onNode(hasClickAction()).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText(actionLabel, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        compose.onNodeWithText(actionLabel).assertIsDisplayed()
        val actionInset = action.left - panel.left
        assertTrue("Mobile CTA should provide a wide touch target", action.width > panel.width * 0.8f)
        assertTrue("Mobile CTA should respect the panel's content inset", actionInset >= 0f && actionInset < panel.width * 0.1f)
        assertTrue("Mobile CTA label should be centered inside its button", abs(label.center.x - action.center.x) < 2f)
        assertActionContained(panel, action, label)
    }

    private fun assertActionContained(
        panel: androidx.compose.ui.geometry.Rect,
        action: androidx.compose.ui.geometry.Rect,
        label: androidx.compose.ui.geometry.Rect,
    ) {
        assertTrue("CTA must fit horizontally inside the panel", action.left >= panel.left && action.right <= panel.right)
        assertTrue("CTA must fit vertically inside the viewport", action.top >= panel.top && action.bottom <= panel.bottom)
        assertTrue("CTA label must fit inside the button", label.left >= action.left && label.right <= action.right &&
            label.top >= action.top && label.bottom <= action.bottom)
        assertTrue("CTA label should be vertically centered: label=$label action=$action", abs(label.center.y - action.center.y) < 2f)
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
        compose.onNodeWithText(movie.title).assertIsDisplayed()
        compose.runOnIdle { trailer.value = false }
        compose.onNodeWithText("2026", substring = true).assertIsDisplayed()
    }
}
