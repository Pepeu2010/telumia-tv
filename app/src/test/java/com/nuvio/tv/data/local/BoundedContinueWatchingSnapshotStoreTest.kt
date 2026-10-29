package com.nuvio.tv.data.local

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BoundedContinueWatchingSnapshotStoreTest {
    private lateinit var directory: java.io.File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("cw-enrichment-cache-test-").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `denied storage write preserves prior snapshot and creates no staging file`() {
        val store = BoundedContinueWatchingSnapshotStore(directory, { 1024L }, { false })
        val existing = directory.resolve("nextup_1.json").apply { writeText("previous") }

        assertFalse(store.write("nextup_1.json", "replacement"))

        assertEquals("previous", existing.readText())
        assertEquals(listOf("nextup_1.json"), directory.list()?.toList())
        assertEquals("previous", store.read("nextup_1.json"))
    }

    @Test
    fun `aggregate metadata quota includes snapshots from other profiles`() {
        val store = BoundedContinueWatchingSnapshotStore(directory, { 10L }, { true })
        val existing = directory.resolve("inprogress_2.json").apply { writeText("123456") }
        val target = directory.resolve("nextup_1.json").apply { writeText("old") }

        assertFalse(store.write("nextup_1.json", "12345"))

        assertEquals("old", target.readText())
        assertEquals("123456", existing.readText())
    }

    @Test
    fun `replacement is allowed at exact aggregate quota and leaves no temporary file`() {
        var checkedBytes = -1L
        val store = BoundedContinueWatchingSnapshotStore(directory, { 10L }, { bytes -> checkedBytes = bytes; true })
        directory.resolve("inprogress_2.json").writeText("123456")
        directory.resolve("nextup_1.json").writeText("old")

        assertTrue(store.write("nextup_1.json", "1234"))

        assertEquals(4L, checkedBytes)
        assertEquals("1234", store.read("nextup_1.json"))
        assertEquals(setOf("inprogress_2.json", "nextup_1.json"), directory.list()?.toSet())
    }

    @Test
    fun `oversized entries and paths outside owned profile names are rejected`() {
        var preflightCalls = 0
        val store = BoundedContinueWatchingSnapshotStore(directory, { 4L }, { preflightCalls++; true })

        assertFalse(store.write("nextup_1.json", "12345"))
        assertFalse(store.write("../../settings.json", "x"))
        assertNull(store.read("inprogress_7.json"))

        assertEquals(0, preflightCalls)
        assertTrue(directory.list().isNullOrEmpty())
    }

    @Test
    fun `metadata allocation reserves ratings capacity before continue watching`() {
        assertEquals(0L, com.nuvio.tv.core.storage.MetadataCachePolicy.allocate(3L * 1024 * 1024).continueWatchingBytes)
        assertEquals(2L * 1024 * 1024, com.nuvio.tv.core.storage.MetadataCachePolicy.allocate(10L * 1024 * 1024).continueWatchingBytes)
        assertEquals(8L * 1024 * 1024, com.nuvio.tv.core.storage.MetadataCachePolicy.allocate(64L * 1024 * 1024).continueWatchingBytes)
    }
}
