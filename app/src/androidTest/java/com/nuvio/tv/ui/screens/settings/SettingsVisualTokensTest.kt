package com.nuvio.tv.ui.screens.settings

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.domain.model.SettingsUiStyle
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsVisualTokensTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun zenSettingsComponentsRenderWithVisibleDpadFocus() {
        renderAndVerify(SettingsUiStyle.ZEN, "zen")
    }

    @Test
    fun horizonSettingsComponentsRenderWithVisibleDpadFocus() {
        renderAndVerify(SettingsUiStyle.HORIZON, "horizon")
    }

    private fun renderAndVerify(style: SettingsUiStyle, screenshotVariant: String) {
        val railFocusRequester = FocusRequester()
        val optionFocusRequester = FocusRequester()
        val screenScrollState = ScrollState(0)

        compose.setContent {
            NuvioTheme(
                settingsUiStyle = style,
                navigationMotion = NavigationMotion.OFF
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NuvioTheme.colors.Background)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.width(200.dp)) {
                            SettingsSectionLabel(
                                text = "Menu",
                                description = "Seções principais"
                            )
                            SettingsRailButton(
                                title = "Aparência",
                                isSelected = true,
                                onClick = {},
                                modifier = Modifier.testTag("settings_visual_rail"),
                                focusRequester = railFocusRequester
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(screenScrollState),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsDetailHeader(
                                title = "Configurações",
                                subtitle = "Controle sua experiência em um só lugar."
                            )
                            SettingsSectionLabel(
                                text = "Aparência",
                                description = "Personalize a tela inicial e os cartões."
                            )
                            SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
                                SettingsActionRow(
                                    title = "Tema ativo",
                                    subtitle = "Paleta escura Telumia",
                                    value = "Telumia",
                                    onClick = {},
                                    modifier = Modifier
                                        .focusRequester(optionFocusRequester)
                                        .testTag("settings_visual_option")
                                )
                                SliderSettingsItem(
                                    title = "Intensidade do cartão",
                                    value = 64,
                                    valueText = "64%",
                                    minValue = 0,
                                    maxValue = 100,
                                    step = 8,
                                    onValueChange = {}
                                )
                                SettingsNote(
                                    text = "As alterações são aplicadas ao perfil ativo."
                                )
                            }
                        }
                    }
                }

                LaunchedEffect(style) {
                    railFocusRequester.requestFocusAfterFrames()
                }
            }
        }

        compose.onNodeWithText("Configurações").assertIsDisplayed()
        compose.onNodeWithText("Aparência").assertIsDisplayed()
        compose.onNodeWithText("Tema ativo").assertIsDisplayed()
        compose.onNodeWithText("Intensidade do cartão").assertIsDisplayed()
        compose.onNodeWithText("As alterações são aplicadas ao perfil ativo.").assertIsDisplayed()
        compose.onNodeWithTag("settings_visual_rail").assertIsFocused()

        val context = instrumentation.targetContext
        val configuration = context.resources.configuration
        val resolution = "${configuration.screenWidthDp}x${configuration.screenHeightDp}dp"
        if (configuration.screenHeightDp <= 360) {
            captureScreenshot(
                context = context,
                name = "telumia-settings-$screenshotVariant-$resolution-top.png"
            )
        }

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("settings_visual_option").assertIsFocused()
        compose.waitForIdle()

        val screenshot = File(
            context.getExternalFilesDir(null) ?: error("External files directory is unavailable"),
            "telumia-settings-$screenshotVariant-$resolution.png"
        )
        if (configuration.screenHeightDp <= 360) {
            compose.onNodeWithText("As alterações são aplicadas ao perfil ativo.")
                .performScrollTo()
            compose.onNodeWithText("As alterações são aplicadas ao perfil ativo.")
                .assertIsDisplayed()
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val compressed = screenshot.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        assertTrue("Settings screenshot should be written: ${screenshot.name}", compressed)
        assertTrue("Settings screenshot should not be empty: ${screenshot.name}", screenshot.length() > 0L)
        context.openFileOutput(screenshot.name, android.content.Context.MODE_PRIVATE).use { internalCopy ->
            screenshot.inputStream().use { it.copyTo(internalCopy) }
        }
    }

    private fun captureScreenshot(context: android.content.Context, name: String) {
        val screenshot = File(context.getExternalFilesDir(null) ?: error("External files directory is unavailable"), name)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val compressed = screenshot.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        assertTrue("Settings screenshot should be written: ${screenshot.name}", compressed)
        assertTrue("Settings screenshot should not be empty: ${screenshot.name}", screenshot.length() > 0L)
        context.openFileOutput(screenshot.name, android.content.Context.MODE_PRIVATE).use { internalCopy ->
            screenshot.inputStream().use { it.copyTo(internalCopy) }
        }
    }
}
