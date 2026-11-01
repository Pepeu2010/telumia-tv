package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LayoutFocusedTrailerSettingsTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitHeroAutoplayCanBeDisabledWithTheRemoteWithoutExpandingCards() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = mutableStateOf(LayoutSettingsUiState(
            selectedLayout = HomeLayout.MODERN,
            modernLandscapePostersEnabled = false,
            focusedPosterBackdropExpandEnabled = false,
            focusedPosterBackdropTrailerEnabled = true,
        ))
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                Column(Modifier.width(700.dp)) {
                    LayoutFocusedPosterSection(state.value) { event ->
                        if (event is LayoutSettingsEvent.SetFocusedPosterBackdropTrailerEnabled) {
                            state.value = state.value.copy(focusedPosterBackdropTrailerEnabled = event.enabled)
                        }
                    }
                }
            }
        }
        val autoplay = compose.onNodeWithText(context.getString(R.string.layout_autoplay_trailer))
        autoplay.assertIsDisplayed().performSemanticsAction(SemanticsActions.RequestFocus)
        autoplay.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertFalse(state.value.focusedPosterBackdropTrailerEnabled)
            assertFalse(state.value.focusedPosterBackdropExpandEnabled)
        }
        compose.onNodeWithText(context.getString(R.string.layout_trailer_muted)).assertDoesNotExist()
    }
}
