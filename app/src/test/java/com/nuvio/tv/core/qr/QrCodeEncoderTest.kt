package com.nuvio.tv.core.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeEncoderTest {
    @Test
    fun `encoded pixels decode to the original login URL`() {
        val payload = "https://nuvio.tv/link?code=ABCD1234"
        val size = 420

        val pixels = encodeQrCodePixels(payload, size, margin = 1)
        val decoded = QRCodeReader().decode(
            BinaryBitmap(
                HybridBinarizer(RGBLuminanceSource(size, size, pixels)),
            ),
        )

        assertEquals(size * size, pixels.size)
        assertEquals(payload, decoded.text)
        assertTrue("The encoder must retain the requested quiet zone", pixels.count { it == -1 } > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank content is rejected before rasterization`() {
        encodeQrCodePixels("  ", size = 420)
    }
}
