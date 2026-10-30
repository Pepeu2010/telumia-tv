package com.nuvio.tv.core.qr

import com.nuvio.tv.data.remote.supabase.DeviceLoginStartResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceLoginQrPayloadTest {
    @Test
    fun `complete verification URL is preferred`() {
        assertEquals(
            "https://login.example/link?code=ABCD",
            resolveDeviceLoginQrPayload(
                verificationUriComplete = "https://login.example/link?code=ABCD",
                verificationUri = "https://login.example/link",
                configuredFallbackUri = "https://fallback.example/link",
            ),
        )
    }

    @Test
    fun `base verification URL is used when complete URL is absent`() {
        assertEquals(
            "https://login.example/device",
            resolveDeviceLoginQrPayload(
                verificationUriComplete = null,
                verificationUri = " https://login.example/device ",
                configuredFallbackUri = "https://fallback.example/link",
            ),
        )
    }

    @Test
    fun `configured URL is used when response URLs are missing or invalid`() {
        assertEquals(
            "https://fallback.example/link",
            resolveDeviceLoginQrPayload(
                verificationUriComplete = "javascript:alert(1)",
                verificationUri = "",
                configuredFallbackUri = "https://fallback.example/link",
            ),
        )
    }

    @Test
    fun `response without complete URL still decodes`() {
        val result = Json.decodeFromString<DeviceLoginStartResult>(
            """{"device_code":"device","user_code":"ABCD","verification_uri":"https://login.example/device","expires_at":"2030-01-01T00:00:00Z"}""",
        )

        assertNull(result.verificationUriComplete)
        assertEquals(
            "https://login.example/device",
            resolveDeviceLoginQrPayload(result.verificationUriComplete, result.verificationUri, null),
        )
    }
}
