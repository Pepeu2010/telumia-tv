package com.nuvio.tv.ui.components

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTvMaterial3Api::class)
class DialogResponsiveLayoutTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dialogFitsCompactLandscapeAndActionStillWorks() {
        var clicked = false
        compose.setContent {
            val compact = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                screenHeightDp = 240
            }
            CompositionLocalProvider(LocalConfiguration provides compact) {
                NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                    NuvioDialog(onDismiss = {}, title = "Editar perfil", suppressFirstKeyUp = false) {
                        Button(onClick = { clicked = true }) { Text("Salvar") }
                    }
                }
            }
        }
        compose.onNodeWithText("Editar perfil").assertIsDisplayed()
        compose.onNodeWithText("Salvar").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(clicked) }
    }
}
