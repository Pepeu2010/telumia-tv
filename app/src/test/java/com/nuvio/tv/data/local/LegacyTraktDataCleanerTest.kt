package com.nuvio.tv.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyTraktDataCleanerTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `purge removes only the retired authentication stores`() {
        val directory = folder.newFolder("datastore")
        val retired = listOf("trakt_auth_store.preferences_pb", "trakt_auth_store_p2.preferences_pb")
        val kept = listOf(
            "trakt_settings.preferences_pb",
            "watch_progress.preferences_pb",
            "auth_session_notice_store.preferences_pb"
        )
        (retired + kept).forEach { File(directory, it).writeText("x") }

        val removed = LegacyTraktDataCleaner.purge(directory)

        assertEquals(retired.size, removed)
        retired.forEach { assertFalse(it, File(directory, it).exists()) }
        kept.forEach { assertTrue(it, File(directory, it).exists()) }
    }

    @Test
    fun `purge tolerates a missing directory`() {
        assertEquals(0, LegacyTraktDataCleaner.purge(File(folder.root, "absent")))
    }
}
