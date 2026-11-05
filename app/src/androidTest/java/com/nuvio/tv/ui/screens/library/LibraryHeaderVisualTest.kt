package com.nuvio.tv.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryHeaderVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cinematicHeaderSupportsFullMotion() = verifyHeader(NavigationMotion.FULL)

    @Test fun cinematicHeaderSupportsReducedMotion() = verifyHeader(NavigationMotion.REDUCED)

    @Test fun cinematicHeaderSupportsMotionOff() = verifyHeader(NavigationMotion.OFF)

    @Test fun floatingMenuClearanceDoesNotLeaveAnEmptyDecorativePanel() {
        compose.setContent {
            NuvioTheme {
                LibraryHeaderPanel("Biblioteca", "TELUMIA", visible = false)
            }
        }
        compose.onNodeWithTag("library-cinematic-header").assertDoesNotExist()
        compose.onNodeWithTag("library-header-title").assertDoesNotExist()
        compose.onNodeWithTag("library-header-source").assertDoesNotExist()
    }

    private fun verifyHeader(motion: NavigationMotion) {
        compose.setContent {
            NuvioTheme(navigationMotion = motion) {
                Box(Modifier.fillMaxSize().background(Color.Black).padding(32.dp)
                    .testTag("library-header-viewport")) {
                    LibraryHeaderPanel("Biblioteca", "TELUMIA", visible = true)
                }
            }
        }
        compose.onNodeWithTag("library-cinematic-header").assertIsDisplayed()
        compose.onNodeWithTag("library-header-title").assertIsDisplayed()
        compose.onNodeWithTag("library-header-source").assertIsDisplayed()
        val viewport = compose.onNodeWithTag("library-header-viewport").getUnclippedBoundsInRoot()
        val header = compose.onNodeWithTag("library-cinematic-header").getUnclippedBoundsInRoot()
        assertTrue(header.left >= viewport.left && header.right <= viewport.right)
        assertTrue(header.top >= viewport.top && header.bottom <= viewport.bottom)
        assertTrue("TV header must preserve a comfortable 48dp+ target height",
            header.bottom - header.top >= 48.dp)
    }
}
