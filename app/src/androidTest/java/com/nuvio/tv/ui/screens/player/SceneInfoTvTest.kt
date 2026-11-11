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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SceneInfoTvTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun chapterContextShowsNeighborsAndDpadCenterSeeksToCurrentStart() {
        val context = TvSceneInfoChapterContext(
            previous = TvSceneInfoEntry("chapter:previous", "A abertura", 0, 45_000),
            current = TvSceneInfoEntry("chapter:current", "A chegada", 45_000, 90_000),
            next = TvSceneInfoEntry("chapter:next", "O retorno", 90_000, 120_000),
        )
        var selectedPosition: Long? = null
        var dismissed = false
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                SceneInfoDialog(
                    context = context,
                    onSeekTo = { selectedPosition = it },
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithTag("scene-info-panel").assertIsDisplayed()
        compose.onNodeWithTag("scene-info-entry-previous").assertIsDisplayed()
        compose.onNodeWithTag("scene-info-entry-current").assertIsDisplayed()
        compose.onNodeWithTag("scene-info-entry-next").assertIsDisplayed()
        compose.onNodeWithText(instrumentation.targetContext.getString(com.nuvio.tv.R.string.scene_info_previous)).assertIsDisplayed()
        compose.onNodeWithText(instrumentation.targetContext.getString(com.nuvio.tv.R.string.scene_info_current)).assertIsDisplayed()
        compose.onNodeWithText(instrumentation.targetContext.getString(com.nuvio.tv.R.string.scene_info_next)).assertIsDisplayed()
        compose.onNodeWithText("A chegada").assertIsDisplayed()
        compose.onNodeWithText("0:45").assertIsDisplayed()
        compose.onNodeWithText("1:30").assertIsDisplayed()
        compose.onNodeWithText(instrumentation.targetContext.getString(com.nuvio.tv.R.string.scene_info_embedded_source)).assertIsDisplayed()
        compose.onNodeWithTag("scene-info-entry-current").assertIsFocused()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/telumia-scene-info-tv.png"
        ).close()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle {
            assertEquals(45_000L, selectedPosition)
            assertTrue(!dismissed)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("scene-info-entry-previous").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(0L, selectedPosition) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("scene-info-entry-current").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("scene-info-entry-next").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(90_000L, selectedPosition) }
    }

    @Test
    fun chapterPanelCanBeDismissedFromTheCloseAction() {
        val context = TvSceneInfoChapterContext(
            previous = null,
            current = TvSceneInfoEntry("chapter:one", "A chegada", 0, 45_000),
            next = null,
        )
        var dismissed = false
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                SceneInfoDialog(context, onSeekTo = {}, onDismiss = { dismissed = true })
            }
        }

        compose.onNodeWithTag("scene-info-entry-current").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("scene-info-close").assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
