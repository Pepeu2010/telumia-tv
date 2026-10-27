package com.nuvio.tv.core.storage

import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCacheOccupancyMonitorTest {
    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun runNext() = tasks.removeFirst().run()
        fun size() = tasks.size
    }

    @Test fun firstAndStaleSnapshotsScheduleBoundedScansWithoutBlockingCaller() {
        val executor = QueuedExecutor()
        var now = 1_000L
        var scans = 0
        var updates = 0
        val monitor = MediaCacheOccupancyMonitor(
            scan = { scans++; MediaCacheOccupancy(scans * 10L, scans, complete = true) },
            executor = executor,
            onSnapshotUpdated = { updates++ },
            clockMillis = { now },
            refreshIntervalMillis = 100L,
        )

        val initial = monitor.current()
        assertEquals(MediaCacheOccupancy(0, 0, complete = false), initial)
        assertEquals(0, scans)
        assertEquals(1, executor.size())

        executor.runNext()
        assertEquals(1, updates)
        assertEquals(MediaCacheOccupancy(10, 1, complete = true), monitor.current())
        assertEquals(1, scans)
        assertEquals(0, executor.size())

        now += 100L
        assertEquals(MediaCacheOccupancy(10, 1, complete = true), monitor.current())
        assertEquals(1, scans)
        assertEquals(1, executor.size())

        executor.runNext()
        assertEquals(2, updates)
        assertEquals(MediaCacheOccupancy(20, 2, complete = true), monitor.current())
        assertEquals(2, scans)
        assertEquals(0, executor.size())
    }

    @Test fun failedScanKeepsLastKnownBytesAndMarksTheSnapshotIncomplete() {
        val executor = QueuedExecutor()
        var now = 0L
        var shouldFail = false
        val monitor = MediaCacheOccupancyMonitor(
            scan = {
                if (shouldFail) error("filesystem unavailable")
                MediaCacheOccupancy(41, 3, complete = true)
            },
            executor = executor,
            clockMillis = { now },
            refreshIntervalMillis = 10L,
        )

        monitor.current()
        executor.runNext()
        assertEquals(MediaCacheOccupancy(41, 3, complete = true), monitor.current())

        now = 10L
        shouldFail = true
        monitor.current()
        executor.runNext()

        val afterFailure = monitor.current()
        assertEquals(41L, afterFailure.bytes)
        assertEquals(3, afterFailure.visitedEntries)
        assertFalse(afterFailure.complete)
        assertEquals(0, executor.size())
    }
}
