package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureReportTest {
    @Test
    fun `report exposes useful playback context without source or account data`() {
        val report = PlaybackFailureReport.create(
            source = PlaybackFailureSource.STREAM,
            stage = PlaybackFailureStage.STARTUP,
            mimeType = "video/mp4; charset=binary",
            mediaUrl = "https://user:pass@private.example/account/movie.mp4?token=signed-value",
            errorCode = "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
            errorMessage = "Connection timed out for https://user:pass@private.example/account/movie.mp4?token=signed-value"
        ).asText()

        assertTrue(report.contains("Source: Stream"))
        assertTrue(report.contains("Stage: Startup"))
        assertTrue(report.contains("Format: MP4"))
        assertTrue(report.contains("First frame: not available"))
        assertTrue(report.contains("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"))
        assertTrue(report.contains("[URL removed]"))
        listOf("private.example", "account/movie", "signed-value", "user:pass").forEach {
            assertFalse("Report leaked $it", report.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `error text removes credentials cookies tokens query values and account data`() {
        val sanitized = sanitizePlaybackError(
            "HTTP failure?secret=query-secret Authorization: Bearer header-secret " +
                "Cookie: session=cookie-secret; refresh=also-secret accountId=acct-123 " +
                "email=person@example.test access_token=token-secret"
        )

        listOf(
            "query-secret", "header-secret", "cookie-secret", "also-secret", "acct-123",
            "person@example.test", "token-secret", "session=cookie-secret"
        ).forEach { assertFalse("Sanitized summary leaked $it", sanitized.contains(it, ignoreCase = true)) }
        assertTrue(sanitized.contains("[redacted]"))
        assertTrue(sanitized.contains("[query removed]"))
        assertTrue(sanitizePlaybackError("Account ID=acct-123 email=person@example.test").contains("[account removed]"))
        assertFalse(sanitizePlaybackError("Failed for private.cdn.example/video?signature=opaque").contains("private.cdn.example"))
        assertFalse(sanitizePlaybackError("Failed for account alice-test").contains("alice-test"))
    }

    @Test
    fun `signed URL parameters are removed even when an error omits the full URL`() {
        val report = PlaybackFailureReport.create(
            source = PlaybackFailureSource.TRAILER,
            stage = PlaybackFailureStage.STARTUP,
            mimeType = "application/vnd.apple.mpegurl",
            mediaUrl = "https://user:pass@cdn.example/private/master.m3u8?X-Amz-Signature=url-signature",
            errorCode = "ERROR_CODE_IO_BAD_HTTP_STATUS",
            errorMessage =
                "403 signature=short-sig X-Amz-Credential=fixture-user X-Amz-Security-Token=temp-token " +
                    "X-Goog-Signature=short-goog CloudFront-Key-Pair-Id=key-1 policy=private-policy"
        ).asText()

        listOf(
            "short-sig", "fixture-user", "temp-token", "short-goog", "key-1", "private-policy",
            "url-signature", "cdn.example", "user:pass"
        ).forEach {
            assertFalse("Sanitized report leaked $it", report.contains(it, ignoreCase = true))
        }
        assertTrue(report.contains("Format: HLS"))
        assertTrue(report.contains("[redacted]"))
    }

    @Test
    fun `report format is inferred from allowlisted MIME or URL extension only`() {
        val hls = PlaybackFailureReport.create(
            source = PlaybackFailureSource.TRAILER,
            stage = PlaybackFailureStage.PLAYBACK,
            mimeType = null,
            mediaUrl = "https://video.example/private/master.m3u8?signature=secret",
            firstFrameRendered = false,
            errorCode = "ERROR_CODE_PARSING_MANIFEST_MALFORMED"
        )
        val unknownMime = PlaybackFailureReport.create(
            source = PlaybackFailureSource.STREAM,
            stage = PlaybackFailureStage.STARTUP,
            mimeType = "application/x-private-token",
            mediaUrl = "https://video.example/stream?account=secret"
        )

        assertEquals("HLS", hls.format)
        assertEquals("not rendered", hls.firstFrame)
        assertEquals("Unknown", unknownMime.format)
        assertEquals("Unknown", unknownMime.errorCode)
        assertFalse(hls.asText().contains("signature"))
    }

    @Test
    fun `long opaque values and filesystem paths are removed from summary`() {
        val longToken = "a".repeat(48)
        val sanitized = sanitizePlaybackError("Failed for C:\\Users\\alice\\file.mp4 token=$longToken")

        assertFalse(sanitized.contains("alice", ignoreCase = true))
        assertFalse(sanitized.contains(longToken))
        assertTrue(sanitized.contains("[path removed]"))
        assertTrue(sanitized.contains("[redacted]"))
    }
}
