package com.nuvio.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupSplashScreenTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun oneShotBrandRevealRendersAtMidSweepWithoutBlockingTheSplash() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.FULL) {
                StartupSplashScreen(
                    profileColorHex = "#FFAA55",
                    profileBackgroundUrl = null,
                )
            }
        }

        compose.mainClock.advanceTimeBy(520L)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.cd_nuvio_logo)).assertIsDisplayed()

        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val output = File(context.filesDir, "telumia-startup-sweep-tv.png")
        output.outputStream().use { stream ->
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }

        compose.mainClock.advanceTimeBy(1_200L)
        compose.onNodeWithContentDescription(context.getString(R.string.cd_nuvio_logo)).assertIsDisplayed()
    }

    @Test fun malformedProfileImageDoesNotPreventBrandInReducedMotion() {
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                StartupSplashScreen(
                    profileColorHex = "invalid-color",
                    profileBackgroundUrl = "file:///invalid image.webp",
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.cd_nuvio_logo)).assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.cd_nuvio_logo)).assertIsDisplayed()
    }
}
