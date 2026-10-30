package com.nuvio.tv.core.qr

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrCodeGeneratorAndroidTest {
    @Test
    fun generatedBitmapCanBeDecodedBackToTheLoginUrl() {
        val payload = "https://nuvio.tv/link?code=ABCD1234"
        val bitmap = QrCodeGenerator.generate(payload, size = 420, margin = 4)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val decoded = QRCodeReader().decode(
            BinaryBitmap(
                HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)),
            ),
        )

        assertEquals(payload, decoded.text)
        bitmap.recycle()
    }
}
