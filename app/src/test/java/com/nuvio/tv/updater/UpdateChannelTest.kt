package com.nuvio.tv.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateChannelTest {
    @Test
    fun prereleaseBuildsStillUseStableUpdates() {
        listOf("1.0.2-alpha.1", "1.0.2-beta.3", "1.0.2-rc.1").forEach { version ->
            assertEquals(UpdateChannel.STABLE, UpdateChannel.defaultForVersion(version))
        }
    }

    @Test
    fun onlyStableStoredChannelIsAccepted() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStoredValue("stable"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStoredValue("STABLE"))
        assertNull(UpdateChannel.fromStoredValue("beta"))
        assertNull(UpdateChannel.fromStoredValue("alpha"))
    }
}
