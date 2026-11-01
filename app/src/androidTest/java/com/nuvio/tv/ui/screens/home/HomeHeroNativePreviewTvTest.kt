package com.nuvio.tv.ui.screens.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.core.player.LocalTrailerPlayerPool
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.player.originalTimelineVideo
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The production hero layer plays locally encoded AVC; no network or simulated ready callback. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class HomeHeroNativePreviewTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstVideoFrameShowsTheBadgeAndLeavingReleasesTheDecoderWithoutMovingFocus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val clip = originalTimelineVideo(context, durationSeconds = 12)
        lateinit var pool: TrailerPlayerPool
        instrumentation.runOnMainSync { pool = TrailerPlayerPool(context, forceNative = false) }
        val playing = mutableStateOf(true)
        val firstFrame = mutableStateOf(false)
        val focus = FocusRequester()
        try {
            compose.setContent {
                NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                    CompositionLocalProvider(LocalTrailerPlayerPool provides pool) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF090D12))) {
                            ModernHeroMediaLayer(
                                heroBackdrop = { null }, enrichmentActive = { false },
                                shouldPlayHeroTrailer = { playing.value },
                                heroTrailerFirstFrameRendered = { firstFrame.value },
                                heroTrailerUrl = { Uri.fromFile(clip).toString() },
                                heroTrailerAudioUrl = { null }, heroTrailerPlaybackKey = { "original-avc" },
                                muted = { true }, onTrailerEnded = { playing.value = false },
                                onFirstFrameRendered = { firstFrame.value = true },
                                modifier = Modifier.fillMaxSize(), requestWidthPx = 960, requestHeightPx = 540,
                            )
                            Button(onClick = {}, modifier = Modifier.align(Alignment.BottomStart).padding(32.dp)
                                .focusRequester(focus).testTag("selected-title")) { Text("Ver detalhes") }
                        }
                        LaunchedEffect(Unit) { focus.requestFocusAfterFrames() }
                    }
                }
            }
            compose.waitUntil(timeoutMillis = 15_000) { firstFrame.value }
            compose.onNodeWithTag("home-preview-playing").assertIsDisplayed().assertHasNoClickAction()
            compose.onNodeWithTag("selected-title").assertIsFocused()
            File(context.filesDir, "telumia-native-hero-preview.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.runOnIdle { playing.value = false; firstFrame.value = false }
            compose.onNodeWithTag("home-preview-playing").assertDoesNotExist()
            compose.onNodeWithTag("selected-title").assertIsFocused()
            compose.runOnIdle { assertNull(pool.activeOwner.value) }
        } finally {
            compose.runOnIdle { playing.value = false }
            compose.waitForIdle()
            instrumentation.runOnMainSync { pool.release() }
            clip.delete()
        }
    }
}
