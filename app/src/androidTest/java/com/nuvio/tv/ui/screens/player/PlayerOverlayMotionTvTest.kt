package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerOverlayMotionTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabledMotionRemovesOverlayWithoutWaitingForFade() {
        val visible = mutableStateOf(true)
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                PlayerOverlayScaffold(visible = visible.value, onDismiss = {}, captureKeys = false) {
                    Box(Modifier.testTag("player-motion-content"))
                }
            }
        }
        compose.onNodeWithTag("player-motion-content").assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("player-motion-content").assertDoesNotExist()
    }

    @Test fun playbackControlsRetainFocusNavigationAndActivation() {
        val first = FocusRequester()
        var plays = 0
        var details = 0
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                Row {
                    ControlButton(icon = Icons.Default.PlayArrow, contentDescription = "Reproduzir",
                        onClick = { plays++ }, focusRequester = first, primary = true)
                    ControlButton(icon = Icons.Default.Info, contentDescription = "Informações",
                        onClick = { details++ })
                }
                LaunchedEffect(Unit) { first.requestFocus() }
            }
        }
        compose.onNodeWithContentDescription("Reproduzir").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5_000) { plays == 1 }
        compose.onNodeWithContentDescription("Reproduzir").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Informações").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(5_000) { details == 1 }
        compose.runOnIdle { assertEquals(1, plays); assertEquals(1, details) }
    }

    @Test fun playbackControlRespondsToAccessibilityAction() {
        var plays = 0
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ControlButton(icon = Icons.Default.PlayArrow, contentDescription = "Reproduzir",
                        onClick = { plays++ }, primary = true)
                }
            }
        }
        compose.onNodeWithContentDescription("Reproduzir").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { plays == 1 }
    }

    @Test fun playbackControlRespondsToTouch() {
        var plays = 0
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ControlButton(icon = Icons.Default.PlayArrow, contentDescription = "Reproduzir",
                        onClick = { plays++ }, primary = true)
                }
            }
        }
        compose.onNodeWithContentDescription("Reproduzir").performTouchInput { click() }
        compose.waitUntil(5_000) { plays == 1 }
    }
}
