package com.nuvio.tv.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoundedContinueWatchingSnapshotStoreInstrumentedTest {
    @Test
    fun deniedWriteKeepsThePriorSnapshotOnTheAndroidFilesystem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "cw-enrichment-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val store = BoundedContinueWatchingSnapshotStore(directory, { 1024L }, { false })
            val previous = File(directory, "inprogress_1.json").apply { writeText("offline-snapshot") }

            assertFalse(store.write("inprogress_1.json", "new-snapshot"))

            assertEquals("offline-snapshot", store.read("inprogress_1.json"))
            assertEquals(listOf(previous.name), directory.list()?.toList())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun successfulWriteUsesTheExactUtf8BytesAndLeavesNoStagingFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "cw-enrichment-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var checkedBytes = -1L
            val store = BoundedContinueWatchingSnapshotStore(directory, { 1024L }, { checkedBytes = it; true })

            assertTrue(store.write("nextup_1.json", "ação"))

            assertEquals(6L, checkedBytes)
            assertEquals("ação", store.read("nextup_1.json"))
            assertEquals(listOf("nextup_1.json"), directory.list()?.toList())
        } finally {
            directory.deleteRecursively()
        }
    }
}
