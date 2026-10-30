package com.nuvio.tv.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateChannelTest {
    @Test
    fun `legacy beta preference migrates to stable`() {
        assertNull(UpdateChannel.fromStoredValue("beta"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.defaultForVersion("1.0.12-alpha.1"))
    }
}
