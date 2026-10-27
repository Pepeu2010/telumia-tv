package com.nuvio.tv.core.storage

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Keeps bounded filesystem scans off callers such as Compose/UI and refreshes their snapshot periodically. */
internal class MediaCacheOccupancyMonitor(
    private val scan: () -> MediaCacheOccupancy,
    private val executor: Executor,
    private val onSnapshotUpdated: () -> Unit = {},
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val refreshIntervalMillis: Long = DEFAULT_REFRESH_INTERVAL_MILLIS,
) {
    companion object {
        const val DEFAULT_REFRESH_INTERVAL_MILLIS = 15_000L
    }

    private data class Snapshot(val occupancy: MediaCacheOccupancy, val measuredAtMillis: Long?)

    private val snapshot = AtomicReference(Snapshot(MediaCacheOccupancy(0, 0, false), null))
    private val scanInFlight = AtomicBoolean(false)

    init { require(refreshIntervalMillis > 0) }

    /** Returns the last sample immediately and schedules a refresh when it is absent or stale. */
    fun current(): MediaCacheOccupancy {
        val current = snapshot.get()
        val now = clockMillis()
        val measuredAt = current.measuredAtMillis
        if (measuredAt == null || now < measuredAt || now - measuredAt >= refreshIntervalMillis) {
            requestRefresh()
        }
        return current.occupancy
    }

    private fun requestRefresh() {
        if (!scanInFlight.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    val previous = snapshot.get()
                    val measured = try {
                        scan()
                    } catch (_: Exception) {
                        previous.occupancy.copy(complete = false)
                    }
                    snapshot.set(Snapshot(measured, clockMillis()))
                    onSnapshotUpdated()
                } finally {
                    scanInFlight.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            scanInFlight.set(false)
        }
    }
}
