package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.domain.model.SettingsUiStyle
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SettingsDesignSystemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modernTrailerSettingRemainsVisibleWhenCardExpansionIsDisabled() {
        val title = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.layout_autoplay_trailer)
        composeRule.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                Column {
                    LayoutFocusedPosterSection(
                        uiState = LayoutSettingsUiState(selectedLayout = HomeLayout.MODERN,
                            modernLandscapePostersEnabled = false, focusedPosterBackdropExpandEnabled = false),
                        onEvent = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText(title).assertExists()
    }

    @Test
    fun railItemAnnouncesSelectionAndRespondsToClickWithMotionOff() {
        val clickCount = AtomicInteger()
        composeRule.setContent {
            NuvioTheme(
                settingsUiStyle = SettingsUiStyle.HORIZON,
                navigationMotion = NavigationMotion.OFF
            ) {
                var selected by remember { mutableStateOf(true) }
                Text(
                    text = if (selected) "selected-state" else "unselected-state",
                    modifier = Modifier.testTag("settings_selected_state"),
                )
                SettingsRailButton(
                    title = "Appearance",
                    isSelected = selected,
                    onClick = {
                        clickCount.incrementAndGet()
                        selected = false
                    }
                )
            }
        }

        val railItem = composeRule.onNodeWithTag(SettingsTestTags.RAIL_ITEM)
        railItem.assertIsSelected()
        railItem.performSemanticsAction(SemanticsActions.RequestFocus)
        railItem.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(1, clickCount.get()) }
        composeRule.onNodeWithTag("settings_selected_state").assertTextEquals("unselected-state")
        railItem.assertIsNotSelected()
    }
}
