package com.nuvio.tv.ui.screens.tmdb

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogoContrastTvTest {
    @Test fun darkMonochromeIsDistinguishedFromColoredAndTransparentArtwork() {
        fun bitmap(color: Int) = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val dark = bitmap(Color.rgb(12, 12, 12))
        val white = bitmap(Color.WHITE)
        val colored = bitmap(Color.rgb(190, 20, 20))
        val transparent = bitmap(Color.TRANSPARENT)
        try {
            assertTrue(isLogoDarkAndMonochrome(dark))
            assertFalse(isLogoDarkAndMonochrome(white))
            assertFalse(isLogoDarkAndMonochrome(colored))
            assertFalse(isLogoDarkAndMonochrome(transparent))
        } finally {
            listOf(dark, white, colored, transparent).forEach(Bitmap::recycle)
        }
    }
}
