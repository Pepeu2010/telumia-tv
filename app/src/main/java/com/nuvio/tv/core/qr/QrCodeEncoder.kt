package com.nuvio.tv.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Encodes crisp black-and-white pixels so rendering does not issue a draw call per module. */
internal fun encodeQrCodePixels(content: String, size: Int, margin: Int = 4): IntArray {
    require(content.isNotBlank()) { "QR content must not be blank" }
    require(size > 0) { "QR size must be positive" }

    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to margin.coerceAtLeast(4)),
    )
    return IntArray(size * size) { index ->
        if (matrix[index % size, index / size]) BLACK else WHITE
    }
}

private const val BLACK = -0x1000000
private const val WHITE = -0x1
