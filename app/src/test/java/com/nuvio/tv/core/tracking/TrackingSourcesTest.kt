package com.nuvio.tv.core.tracking

import com.nuvio.tv.data.local.WatchProgressSource
import com.nuvio.tv.domain.model.LibrarySourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingSourcesTest {
    @Test
    fun `MDBList is available independently for watch and library sources`() {
        val connected = setOf(TrackingProviderId.SIMKL, TrackingProviderId.MDBLIST)
        assertEquals(WatchProgressSource.MDBLIST, WatchProgressSource.fromStorage("MDBLIST"))
        assertEquals(TrackingProviderId.MDBLIST, WatchProgressSource.MDBLIST.providerId)
        assertEquals(
            listOf(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.SIMKL, WatchProgressSource.MDBLIST),
            availableWatchProgressSources(connected)
        )
        assertEquals(
            listOf(LibrarySourceMode.LOCAL, LibrarySourceMode.SIMKL, LibrarySourceMode.MDBLIST),
            availableLibrarySourceModes(connected)
        )
        val selection = TrackingSourceSelection(WatchProgressSource.MDBLIST, LibrarySourceMode.SIMKL)
        assertEquals(selection, effectiveTrackingSourceSelection(selection, connected))
        assertEquals(
            selection.copy(watchProgressSource = WatchProgressSource.NUVIO_SYNC),
            effectiveTrackingSourceSelection(selection, connected - TrackingProviderId.MDBLIST)
        )
    }

    @Test
    fun `stored source names keep their meaning and retired names fall back to local sources`() {
        assertEquals(WatchProgressSource.SIMKL, WatchProgressSource.fromStorage("SIMKL"))
        assertEquals(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.fromStorage("NUVIO_SYNC"))
        // Values persisted by the retired Trakt integration must not crash and must resolve to local data.
        assertEquals(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.fromStorage("TRAKT"))
        assertEquals(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.fromStorage(null))
        assertNull(LibrarySourceMode.entries.firstOrNull { it.name == "TRAKT" })
        assertNull(TrackingProviderId.fromStorage("trakt"))
    }

    @Test
    fun `remote watch source falls back to Nuvio Sync when disconnected`() {
        assertEquals(
            WatchProgressSource.NUVIO_SYNC,
            effectiveWatchProgressSource(WatchProgressSource.SIMKL) { false }
        )
        assertEquals(
            WatchProgressSource.SIMKL,
            effectiveWatchProgressSource(WatchProgressSource.SIMKL) { it == TrackingProviderId.SIMKL }
        )
    }

    @Test
    fun `remote library source falls back to local when disconnected`() {
        assertEquals(
            LibrarySourceMode.LOCAL,
            effectiveLibrarySourceMode(LibrarySourceMode.SIMKL) { false }
        )
        assertEquals(
            LibrarySourceMode.SIMKL,
            effectiveLibrarySourceMode(LibrarySourceMode.SIMKL) { it == TrackingProviderId.SIMKL }
        )
    }

    @Test
    fun `local sources do not map to remote providers`() {
        assertNull(WatchProgressSource.NUVIO_SYNC.providerId)
        assertNull(LibrarySourceMode.LOCAL.providerId)
    }

    @Test
    fun `source reconciliation covers every provider connection combination`() {
        val requested = TrackingSourceSelection(
            watchProgressSource = WatchProgressSource.SIMKL,
            librarySourceMode = LibrarySourceMode.MDBLIST
        )

        assertEquals(
            TrackingSourceSelection(WatchProgressSource.NUVIO_SYNC, LibrarySourceMode.LOCAL),
            effectiveTrackingSourceSelection(requested, emptySet())
        )
        assertEquals(
            TrackingSourceSelection(WatchProgressSource.NUVIO_SYNC, LibrarySourceMode.MDBLIST),
            effectiveTrackingSourceSelection(requested, setOf(TrackingProviderId.MDBLIST))
        )
        assertEquals(
            TrackingSourceSelection(WatchProgressSource.SIMKL, LibrarySourceMode.LOCAL),
            effectiveTrackingSourceSelection(requested, setOf(TrackingProviderId.SIMKL))
        )
        assertEquals(
            requested,
            effectiveTrackingSourceSelection(
                requested,
                setOf(TrackingProviderId.MDBLIST, TrackingProviderId.SIMKL)
            )
        )
    }

    @Test
    fun `disconnecting an active provider resets only its selected source`() {
        val requested = TrackingSourceSelection(
            watchProgressSource = WatchProgressSource.SIMKL,
            librarySourceMode = LibrarySourceMode.MDBLIST
        )

        assertEquals(
            TrackingSourceSelection(
                watchProgressSource = WatchProgressSource.NUVIO_SYNC,
                librarySourceMode = LibrarySourceMode.MDBLIST
            ),
            effectiveTrackingSourceSelection(requested, setOf(TrackingProviderId.MDBLIST))
        )
    }

    @Test
    fun `disconnecting an inactive provider preserves selected sources`() {
        val requested = TrackingSourceSelection(
            watchProgressSource = WatchProgressSource.MDBLIST,
            librarySourceMode = LibrarySourceMode.MDBLIST
        )

        assertEquals(
            requested,
            effectiveTrackingSourceSelection(requested, setOf(TrackingProviderId.MDBLIST))
        )
    }

    @Test
    fun `both connected pickers expose Nuvio Simkl and MDBList in stable order`() {
        val connected = setOf(TrackingProviderId.MDBLIST, TrackingProviderId.SIMKL)

        assertEquals(
            listOf(
                WatchProgressSource.NUVIO_SYNC,
                WatchProgressSource.SIMKL,
                WatchProgressSource.MDBLIST
            ),
            availableWatchProgressSources(connected)
        )
        assertEquals(
            listOf(
                LibrarySourceMode.LOCAL,
                LibrarySourceMode.SIMKL,
                LibrarySourceMode.MDBLIST
            ),
            availableLibrarySourceModes(connected)
        )
    }

    @Test
    fun `disconnected providers are excluded from source pickers`() {
        assertEquals(
            listOf(WatchProgressSource.NUVIO_SYNC),
            availableWatchProgressSources(emptySet())
        )
        assertEquals(
            listOf(LibrarySourceMode.LOCAL),
            availableLibrarySourceModes(emptySet())
        )
        assertEquals(
            listOf(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.SIMKL),
            availableWatchProgressSources(setOf(TrackingProviderId.SIMKL))
        )
        assertEquals(
            listOf(LibrarySourceMode.LOCAL, LibrarySourceMode.MDBLIST),
            availableLibrarySourceModes(setOf(TrackingProviderId.MDBLIST))
        )
    }
}
