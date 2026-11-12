package com.nuvio.tv.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.core.player.LocalTrailerPlayerPool
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.screens.player.originalTimelineVideo
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Environment sensitivity (reproduced): the offscreen gradient layer in the pixel test passes on a
 * freshly booted software-rendered (SwiftShader) emulator and can fail after the emulator has been
 * running other GL work for a while. The product Home draws the gradient correctly. The video test
 * runs after the pure Compose test to keep the result deterministic within one run.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class HomeTrailerCompositeTvTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun backdropExtendsBehindNavigationAndFadesAtVisibleBottom() {
        val backdrop = File(context.cacheDir, "telumia-screen-backdrop-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.GREEN) }
        backdrop.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val url = Uri.fromFile(backdrop).toString()
        val previousDisplayedUrl = HeroBackdropState.lastDisplayedUrl
        val previousCurrentUrl = HeroBackdropState.consumeAndClear()
        HeroBackdropState.lastDisplayedUrl = url
        try {
            compose.setContent {
                NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(88.dp))
                        Box(Modifier.weight(1f)) {
                            ModernHeroScene(
                                state = { ModernHeroSceneState(url, null, false, false, false, null, null, null, false, true) },
                                isFullScreen = { true }, bgColor = Color.Black,
                                modifier = homeHeroMediaModifier(true, screenHeight, screenHeight, false),
                                requestWidthPx = context.resources.displayMetrics.widthPixels,
                                requestHeightPx = context.resources.displayMetrics.heightPixels,
                                onTrailerEnded = {}, onFirstFrameRendered = {}
                            )
                        }
                    }
                }
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                val shot = instrumentation.uiAutomation.takeScreenshot()
                val green = AndroidColor.green(shot.getPixel(shot.width * 9 / 10, shot.height / 2))
                shot.recycle()
                green > 120
            }
            val shot = instrumentation.uiAutomation.takeScreenshot()
            try {
                fun green(x: Int, y: Int) = AndroidColor.green(shot.getPixel(shot.width * x / 100, shot.height * y / 100))
                assertTrue("backdrop must extend behind navigation", green(90, 5) > 120)
                assertTrue("backdrop must remain visible near the bottom", green(90, 90) > 20)
                assertTrue("bottom edge must fade: g90=${green(90, 90)} g95=${green(90, 95)} g97=${green(90, 97)} g99=${green(90, 99)}", green(90, 99) < green(90, 90))
                assertTrue("left gradient must preserve text contrast", green(5, 50) < green(90, 50) / 2)
            } finally { shot.recycle() }
        } finally {
            backdrop.delete()
            previousCurrentUrl?.let(HeroBackdropState::update)
            HeroBackdropState.lastDisplayedUrl = previousDisplayedUrl
        }
    }

    @Test
    fun posterRemainsVisibleAcrossTheRealFirstFrameCrossfade() {
        val backdrop = File(context.cacheDir, "telumia-home-backdrop-${System.nanoTime()}.png")
        val backdropBitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(AndroidColor.GREEN)
        }
        backdrop.outputStream().use { check(backdropBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        backdropBitmap.recycle()

        val trailer = originalTimelineVideo(context)
        val trailerUrl = Uri.fromFile(trailer).toString()
        val backdropUrl = Uri.fromFile(backdrop).toString()
        val shouldPlay = mutableStateOf(true)
        val firstFrame = mutableStateOf(false)
        val firstFrameSeen = AtomicBoolean(false)
        val pool = TrailerPlayerPool(context, forceNative = false)
        val previousDisplayedUrl = HeroBackdropState.lastDisplayedUrl
        val previousCurrentUrl = HeroBackdropState.consumeAndClear()
        HeroBackdropState.lastDisplayedUrl = backdropUrl

        try {
            compose.setContent {
                NuvioTheme(navigationMotion = NavigationMotion.FULL) {
                    CompositionLocalProvider(LocalTrailerPlayerPool provides pool) {
                        androidx.compose.runtime.LaunchedEffect(shouldPlay.value, trailerUrl) {
                            firstFrame.value = false
                            firstFrameSeen.set(false)
                        }
                        Box(Modifier.fillMaxSize()) {
                            ModernHeroMediaLayer(
                                heroBackdrop = { backdropUrl },
                                enrichmentActive = { false },
                                shouldPlayHeroTrailer = { shouldPlay.value },
                                heroTrailerFirstFrameRendered = { firstFrame.value },
                                heroTrailerUrl = { trailerUrl },
                                heroTrailerAudioUrl = { null },
                                heroTrailerPlaybackKey = { trailerUrl },
                                muted = { false },
                                onTrailerEnded = {},
                                onFirstFrameRendered = {
                                    firstFrame.value = true
                                    firstFrameSeen.set(true)
                                },
                                modifier = Modifier.fillMaxSize(),
                                requestWidthPx = context.resources.displayMetrics.widthPixels,
                                requestHeightPx = context.resources.displayMetrics.heightPixels
                            )
                        }
                    }
                }
            }

            compose.waitUntil(timeoutMillis = 12_000) { firstFrameSeen.get() }
            compose.runOnIdle {
                val owner = pool.activeOwner.value
                assertNotNull("The preview must own the shared player", owner)
                assertEquals("The preview must start with sound enabled", 1f, pool.acquire(requireNotNull(owner))!!.volume, 0.001f)
            }

            // Sample through the overlapping image/video alpha transitions. A black
            // framebuffer here means both layers disappeared before video pixels arrived.
            fun assertTransitionNeverGoesBlack(samplePrefix: String) {
                listOf(0L, 60L, 140L, 260L, 420L, 700L).forEachIndexed { index, delayMs ->
                    if (delayMs > 0) Thread.sleep(delayMs)
                    instrumentation.waitForIdleSync()
                    val screenshot = instrumentation.uiAutomation.takeScreenshot()
                    val center = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
                    val brightness = AndroidColor.red(center) + AndroidColor.green(center) + AndroidColor.blue(center)
                    if (brightness <= 90) {
                        val failureFrame = File(context.getExternalFilesDir(null), "home-trailer-black-transition-$samplePrefix-$index.png")
                        failureFrame.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    screenshot.recycle()
                    assertTrue("Home trailer crossfade exposed a black frame at $samplePrefix sample $index (RGB sum=$brightness)", brightness > 90)
                }
            }
            assertTransitionNeverGoesBlack("first")

            // Replaying the same media key must clear readiness and wait for its new frame.
            compose.runOnIdle { shouldPlay.value = false }
            compose.waitUntil(timeoutMillis = 5_000) { pool.activeOwner.value == null && !firstFrameSeen.get() }
            compose.runOnIdle { shouldPlay.value = true }
            compose.waitUntil(timeoutMillis = 12_000) { firstFrameSeen.get() }
            assertTransitionNeverGoesBlack("restart")
        } finally {
            compose.runOnIdle { shouldPlay.value = false }
            compose.waitUntil(timeoutMillis = 5_000) { pool.activeOwner.value == null }
            instrumentation.runOnMainSync { pool.release() }
            backdrop.delete()
            trailer.delete()
            previousCurrentUrl?.let(HeroBackdropState::update)
            HeroBackdropState.lastDisplayedUrl = previousDisplayedUrl
        }
    }
}
