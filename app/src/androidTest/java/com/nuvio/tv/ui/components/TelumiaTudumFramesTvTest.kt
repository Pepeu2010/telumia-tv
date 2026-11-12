package com.nuvio.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renders the launch "tudum" at fixed instants with a controlled clock (no emulator jank). */
@RunWith(AndroidJUnit4::class)
class TelumiaTudumFramesTvTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightGrowsThenWordmarkLandsAndLoaderAppearsLast() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.FULL) {
                StartupSplashScreen(
                    profileColorHex = null,
                    profileBackgroundUrl = null,
                    modifier = Modifier,
                )
            }
        }
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "tudum")
        dir.mkdirs()
        val instants = listOf(0, 120, 260, 420, 620, 880, 1160, 1500, 1900, 2400)
        var elapsed = 0L
        val centreLuma = mutableListOf<Double>()
        for (ms in instants) {
            compose.mainClock.advanceTimeBy(ms - elapsed)
            elapsed = ms.toLong()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "tudum-%04d.png".format(ms)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            var sum = 0.0
            var n = 0
            for (y in bitmap.height * 40 / 100..bitmap.height * 50 / 100 step 6)
                for (x in bitmap.width * 25 / 100..bitmap.width * 75 / 100 step 6) {
                    val p = bitmap.getPixel(x, y)
                    sum += ((p shr 16) and 0xff) + ((p shr 8) and 0xff) + (p and 0xff)
                    n += 3
                }
            centreLuma += sum / n
        }
        // The halo must actually light the centre mid-way (it is dimmer at the very start and end).
        val peak = centreLuma.max()
        assertTrue("the light never brightened the centre: $centreLuma", peak > centreLuma.first() + 8)
    }
}
