package com.nuvio.tv.core.qr

import android.graphics.Bitmap

object QrCodeGenerator {

    fun generate(content: String, size: Int = 512, margin: Int = 4): Bitmap {
        val pixels = encodeQrCodePixels(content, size, margin)
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }
}
