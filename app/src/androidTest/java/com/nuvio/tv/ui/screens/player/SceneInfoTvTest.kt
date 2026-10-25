package com.nuvio.tv.ui.screens.player

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SceneInfoTvTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun chapterPanelStartsFocusedAndDismissesWithDpadCenter() {
        val entries = listOf(TvSceneInfoEntry("chapter:one", "A chegada", 0, 45_000))
        var dismissed = false
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                SceneInfoDialog(entries, onDismiss = { dismissed = true })
            }
        }

        compose.onNodeWithTag("scene-info-panel").assertIsDisplayed()
        compose.onNodeWithTag("scene-info-entry-0").assertIsDisplayed()
        compose.onNodeWithText("A chegada").assertIsDisplayed()
        compose.onNodeWithText(instrumentation.targetContext.getString(com.nuvio.tv.R.string.scene_info_embedded_source)).assertIsDisplayed()
        compose.onNodeWithTag("scene-info-close").assertIsFocused()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/telumia-scene-info-tv.png"
        ).close()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
