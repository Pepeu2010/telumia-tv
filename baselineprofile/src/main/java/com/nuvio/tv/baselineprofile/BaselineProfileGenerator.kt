package com.nuvio.tv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ONBOARDING_FIRST_LABEL = "Continue without account"

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val targetPackage = BuildConfig.TARGET_APPLICATION_ID
        rule.collect(
            packageName = targetPackage,
            // Keep collection bounded; instability is still reported by the collector.
            maxIterations = 5,
            includeInStartupProfile = true
        ) {
            // Force English locale so onboarding labels are predictable regardless of device language.
            val originalSystemLocales = device.executeShellCommand("settings get system system_locales").trim()
            device.executeShellCommand("settings put system system_locales en-US")

            try {
                pressHome()
                startActivityAndWait()
                device.wait(Until.hasObject(By.pkg(targetPackage)), 5_000)
                device.waitForIdle()

                // Step through onboarding once; subsequent iterations already open on Home.
                if (device.wait(Until.hasObject(By.text(ONBOARDING_FIRST_LABEL)), 8_000) != null) {
                    device.focusAndSelect(ONBOARDING_FIRST_LABEL)
                    device.focusAndSelect("Advanced")
                    device.focusAndSelect("Continue")
                }
                device.waitForIdle()
                Thread.sleep(3_000)

                // Move focus off the sidebar into the content grid.
                device.pressDPadRight()
                device.waitForIdle()

                // Scroll across the focused row to compile the row and card rendering paths.
                repeat(6) {
                    device.pressDPadRight()
                    device.waitForIdle()
                    Thread.sleep(120)
                }

                // Scroll down through the rows to compile the vertical list and prefetch paths.
                repeat(6) {
                    device.pressDPadDown()
                    device.waitForIdle()
                    Thread.sleep(150)
                }

                // Move back up toward a focused title.
                repeat(2) {
                    device.pressDPadUp()
                    device.waitForIdle()
                    Thread.sleep(120)
                }
            } finally {
                // Restore the original system locale, including the unset state.
                if (originalSystemLocales.isBlank() || originalSystemLocales == "null") {
                    device.executeShellCommand("settings delete system system_locales")
                } else {
                    device.executeShellCommand("settings put system system_locales $originalSystemLocales")
                }
            }
        }
    }
}

// Moves D-pad focus until the control carrying the given label is focused and then selects it, since TV devices have no touch input.
private fun UiDevice.focusAndSelect(label: String, maxSteps: Int = 24): Boolean {
    val pattern = Pattern.compile(Pattern.quote(label), Pattern.CASE_INSENSITIVE)
    if (wait(Until.hasObject(By.text(pattern)), 10_000) == null) return false
    repeat(maxSteps) { step ->
        if (isFocusedLabel(label, pattern)) {
            pressDPadCenter()
            waitForIdle()
            Thread.sleep(1_500)
            // Treat the step as done only once the label is gone, so a mis-selection keeps searching instead of moving on.
            if (wait(Until.gone(By.text(pattern)), 5_000) == true) return true
        }
        // Alternate directions so both vertical lists and side-by-side cards are reachable.
        if (step % 2 == 0) pressDPadDown() else pressDPadRight()
        waitForIdle()
        Thread.sleep(250)
    }
    return false
}

// Reports whether the currently focused control carries the label, treating nodes that disappear mid-query as a non-match.
private fun UiDevice.isFocusedLabel(label: String, pattern: Pattern): Boolean {
    return try {
        val focused = findObject(By.focused(true)) ?: return false
        if (focused.text?.equals(label, ignoreCase = true) == true) return true
        // Require the focused node to be the clickable control itself so a large parent container is not mistaken for the button.
        focused.isClickable && focused.findObject(By.text(pattern)) != null
    } catch (e: StaleObjectException) {
        false
    }
}
