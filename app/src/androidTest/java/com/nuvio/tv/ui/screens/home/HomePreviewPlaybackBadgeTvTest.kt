package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import org.junit.Rule
import org.junit.Test

class HomePreviewPlaybackBadgeTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playbackStatusNeverStealsTheRemoteFocusAndDisappearsWhenPlaybackStops() {
        val playing = mutableStateOf(false)
        val muted = mutableStateOf(true)
        val focus = FocusRequester()
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                Column {
                    HomePreviewPlaybackBadge(visible = playing.value, muted = muted.value)
                    Button(onClick = {}, modifier = Modifier.focusRequester(focus).testTag("selected-title")) {
                        Text("Ver detalhes")
                    }
                }
                LaunchedEffect(Unit) { focus.requestFocusAfterFrames() }
            }
        }
        compose.onNodeWithTag("home-preview-playing").assertDoesNotExist()
        compose.onNodeWithTag("selected-title").assertIsFocused()
        compose.runOnIdle { playing.value = true }
        compose.onNodeWithTag("home-preview-playing").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithTag("selected-title").assertIsFocused()
        compose.runOnIdle { muted.value = false }
        compose.onNodeWithTag("selected-title").assertIsFocused()
        compose.runOnIdle { playing.value = false }
        compose.onNodeWithTag("home-preview-playing").assertDoesNotExist()
        compose.onNodeWithTag("selected-title").assertIsFocused()
    }
}
