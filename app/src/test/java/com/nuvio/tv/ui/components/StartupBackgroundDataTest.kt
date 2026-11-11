package com.nuvio.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StartupBackgroundDataTest {
    @Test
    fun fileUriIsConvertedForImageLoading() {
        assertEquals(File("/tmp/telumia image.png"), startupBackgroundData("file:///tmp/telumia%20image.png"))
    }

    @Test
    fun malformedAndRemoteAddressesDoNotCrashStartup() {
        val malformed = "file:///tmp/invalid image.png"
        val remote = "https://example.com/background.webp"
        assertEquals(malformed, startupBackgroundData(malformed))
        assertEquals(remote, startupBackgroundData(remote))
    }
}
