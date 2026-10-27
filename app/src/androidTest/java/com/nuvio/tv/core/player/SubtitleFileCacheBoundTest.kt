package com.nuvio.tv.core.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SubtitleFileCacheBoundTest {
    @Test
    fun oversizedRemoteSubtitleIsSkippedWithoutLeavingAPartialFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheDir = File(context.cacheDir, "subtitles")
        val server = MockWebServer()
        val cache = SubtitleFileCache(context, OkHttpClient())
        val validSubtitle = "1\n00:00:01,000 --> 00:00:02,000\nLegendas\n"
        server.enqueue(MockResponse().setBody(validSubtitle))
        server.enqueue(
            MockResponse().setBody(Buffer().write(ByteArray(MAX_SUBTITLE_BYTES + 1) { 'a'.code.toByte() }))
        )
        server.start()
        try {
            cache.clearCache()
            val valid = cache.cacheSubtitles(
                listOf(SubtitleInput(server.url("/valid.srt").toString(), "valid", "pt")),
            )
            assertTrue(valid?.singleOrNull()?.url?.startsWith("content://") == true)
            assertTrue(File(cacheDir, "pt_valid.srt").readText() == validSubtitle)
            cache.clearCache()

            val result = cache.cacheSubtitles(
                listOf(SubtitleInput(server.url("/oversized.srt").toString(), "oversized", "en")),
            )

            assertNull(result)
            assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
        } finally {
            cache.clearCache()
            server.shutdown()
        }
    }

    private companion object {
        const val MAX_SUBTITLE_BYTES = 4 * 1024 * 1024
    }
}
