package com.nuvio.tv.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupSplashMotionTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun brandAppearsWithMotionEnabled() = verifyBrand(NavigationMotion.FULL)
    @Test fun brandAppearsWithMotionDisabled() = verifyBrand(NavigationMotion.OFF)

    @Test fun brandAppearsWithReducedMotion() = verifyBrand(NavigationMotion.REDUCED)

    @Test fun malformedLocalBackgroundDoesNotCrashStartup() =
        verifyBrand(NavigationMotion.OFF, "file://invalid host/background image.webp")

    private fun verifyBrand(motion: NavigationMotion, backgroundUrl: String? = null) {
        compose.setContent {
            NuvioTheme(navigationMotion = motion) {
                StartupSplashScreen(profileColorHex = null, profileBackgroundUrl = backgroundUrl)
            }
        }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.cd_nuvio_logo)
        compose.onNodeWithContentDescription(label).assertExists()
    }
}
