package com.nuvio.tv.ui.screens.search

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanySearchStatusTvTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingConfigurationExplainsFailureWithoutFutileRetry() {
        val error = context.getString(R.string.search_error_tmdb_api_key_missing)
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            CompanySearchResultsSection(emptyList(), false, error, FocusRequester(), mutableMapOf(), {}, { _, _ -> }, {})
        } }
        compose.onNodeWithText(error).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_retry)).assertDoesNotExist()
    }

    @Test fun transientFailureStillOffersRetry() {
        var retries = 0
        compose.setContent { NuvioTheme(navigationMotion = NavigationMotion.OFF) {
            CompanySearchResultsSection(emptyList(), false, "temporary", FocusRequester(), mutableMapOf(), {}, { _, _ -> }, { retries++ })
        } }
        compose.onNodeWithText(context.getString(R.string.action_retry)).performSemanticsAction(SemanticsActions.RequestFocus)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
