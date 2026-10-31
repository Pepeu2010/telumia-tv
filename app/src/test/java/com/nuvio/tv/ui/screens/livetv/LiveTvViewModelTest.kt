package com.nuvio.tv.ui.screens.livetv

import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.livetv.LiveTvRefreshScheduler
import com.nuvio.tv.core.livetv.LiveTvSourceImporter
import com.nuvio.tv.core.livetv.LiveTvGuideCache
import com.nuvio.tv.core.livetv.CachedLiveTvGuide
import com.nuvio.tv.data.local.LiveTvSourceStore
import com.nuvio.tv.data.local.LiveTvChannelState
import com.nuvio.tv.data.local.LiveTvChannelStateStore
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvImportResult
import com.nuvio.tv.domain.model.LiveTvProgram
import com.nuvio.tv.domain.model.LiveTvSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun addingSourcePersistsConfigurationAndPublishesImportedChannels() = runTest(mainDispatcher.dispatcher) {
        val store = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        val source = LiveTvSource("source-1", "Brasil", "https://example.org/list.m3u")
        val channel = LiveTvChannel("source-1:channel", "source-1", "Canal Brasil", "https://example.org/live.m3u8")
        coEvery { store.list() } returns emptyList()
        coEvery { store.add("Brasil", source.playlistUrl, null) } returns source
        coEvery { importer.import(source) } returns LiveTvImportResult(listOf(channel))
        coEvery { guideCache.read(source.id) } returns null
        coEvery { guideCache.write(source.id, any()) } returns true
        coEvery { channelState.read() } returns LiveTvChannelState()
        val viewModel = LiveTvViewModel(store, importer, guideCache, channelState, refreshScheduler, activeProfiles())

        runCurrent()
        viewModel.addSource("Brasil", source.playlistUrl, null)
        runCurrent()

        assertEquals(listOf(source), viewModel.state.value.sources)
        assertEquals(listOf(channel), viewModel.state.value.channels)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.backgroundRefreshUnavailable)
        coVerify(exactly = 1) { store.add("Brasil", source.playlistUrl, null) }
        coVerify(exactly = 1) { importer.import(source) }
        coVerify(exactly = 1) { guideCache.write(source.id, LiveTvImportResult(listOf(channel)), any()) }
        coVerify(exactly = 1) { refreshScheduler.schedule() }
        coVerify(exactly = 1) { refreshScheduler.schedule() }
    }

    @Test
    fun addingSourceStillImportsChannelsWhenBackgroundSchedulingFails() = runTest(mainDispatcher.dispatcher) {
        val store = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>()
        val source = LiveTvSource("source-1", "Brasil", "https://example.org/list.m3u")
        val channel = LiveTvChannel("source-1:channel", "source-1", "Canal Brasil", "https://example.org/live.m3u8")
        coEvery { store.list() } returns emptyList()
        coEvery { store.add("Brasil", source.playlistUrl, null) } returns source
        coEvery { importer.import(source) } returns LiveTvImportResult(listOf(channel))
        coEvery { guideCache.read(source.id) } returns null
        coEvery { guideCache.write(source.id, any()) } returns true
        coEvery { channelState.read() } returns LiveTvChannelState()
        every { refreshScheduler.cancel() } returns Unit
        every { refreshScheduler.schedule() } throws IllegalStateException("WorkManager unavailable")
        val viewModel = LiveTvViewModel(store, importer, guideCache, channelState, refreshScheduler, activeProfiles())

        runCurrent()
        viewModel.addSource("Brasil", source.playlistUrl, null)
        runCurrent()

        assertEquals(listOf(channel), viewModel.state.value.channels)
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.backgroundRefreshUnavailable)
        coVerify(exactly = 1) { importer.import(source) }
    }

    @Test
    fun disabledSourceIsNotFetched() = runTest(mainDispatcher.dispatcher) {
        val store = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        val source = LiveTvSource("source-1", "Brasil", "https://example.org/list.m3u", enabled = false)
        coEvery { store.list() } returns listOf(source)
        coEvery { channelState.read() } returns LiveTvChannelState()
        val viewModel = LiveTvViewModel(store, importer, guideCache, channelState, refreshScheduler, activeProfiles())

        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(source), viewModel.state.value.sources)
        assertEquals(emptyList<LiveTvChannel>(), viewModel.state.value.channels)
        coVerify(exactly = 0) { importer.import(any()) }
        coVerify(exactly = 0) { refreshScheduler.schedule() }
        coVerify(exactly = 1) { refreshScheduler.cancel() }
        coVerify(exactly = 0) { refreshScheduler.schedule() }
        coVerify(exactly = 1) { refreshScheduler.cancel() }
    }

    @Test
    fun startupShowsCachedGuideAndRefreshKeepsItWhenNetworkFails() = runTest(mainDispatcher.dispatcher) {
        val store = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        val source = LiveTvSource("source-1", "Brasil", "https://example.org/list.m3u")
        val channel = LiveTvChannel("source-1:channel", "source-1", "Canal Brasil", "https://example.org/live.m3u8")
        val cached = LiveTvImportResult(listOf(channel))
        coEvery { store.list() } returns listOf(source)
        coEvery { guideCache.read(source.id) } returns CachedLiveTvGuide(sourceId = source.id, updatedEpochMillis = 100L, result = cached)
        coEvery { importer.import(source) } throws IllegalStateException("offline")
        coEvery { channelState.read() } returns LiveTvChannelState()
        val viewModel = LiveTvViewModel(store, importer, guideCache, channelState, refreshScheduler, activeProfiles())

        runCurrent()
        assertEquals(listOf(channel), viewModel.state.value.channels)
        assertEquals(mapOf(source.id to 100L), viewModel.state.value.lastUpdatedEpochMillis)
        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(channel), viewModel.state.value.channels)
        assertEquals("offline", viewModel.state.value.sourceErrors[source.id])
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun persistedReadOnlyGuideIsShownAtStartupAndSurvivesRefreshWhenOffline() = runTest(mainDispatcher.dispatcher) {
        val source = LiveTvSource("source-offline", "Brasil", "https://example.org/list.m3u")
        val channel = LiveTvChannel(
            "source-offline:channel",
            source.id,
            "Canal Brasil",
            "https://example.org/live.m3u8",
            tvgId = "canal-brasil",
        )
        val program = LiveTvProgram(
            sourceId = source.id,
            channelId = "canal-brasil",
            title = "Jornal da Noite",
            startEpochMillis = 1_000L,
            endEpochMillis = 2_000L,
            description = "Edição nacional",
        )
        val expected = LiveTvImportResult(channels = listOf(channel), programs = listOf(program))
        val cacheDirectory = temporaryFolder.newFolder("persisted-guide")
        val onlineCache = LiveTvGuideCache(
            cacheDirectory,
            quotaBytes = 1024L * 1024L,
            writesEnabled = true,
            atomicReplace = { temporary, target ->
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                true
            },
        )
        assertTrue(onlineCache.write(source.id, expected, updatedEpochMillis = 1234L))

        // Reopen the real persisted cache in read-only mode, as happens when offline or over quota.
        val readOnlyCache = LiveTvGuideCache(
            cacheDirectory,
            quotaBytes = 1024L * 1024L,
            writesEnabled = false,
        )
        assertEquals(expected, readOnlyCache.read(source.id)?.result)
        val store = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        coEvery { store.list() } returns listOf(source)
        coEvery { importer.import(source) } throws IllegalStateException("offline")
        coEvery { channelState.read() } returns LiveTvChannelState()
        val viewModel = LiveTvViewModel(store, importer, readOnlyCache, channelState, refreshScheduler, activeProfiles())

        runCurrent()
        viewModel.state.first { !it.isLoading }
        assertEquals(listOf(channel), viewModel.state.value.channels)
        assertEquals(listOf(program), viewModel.state.value.programs)
        assertEquals(mapOf(source.id to 1234L), viewModel.state.value.lastUpdatedEpochMillis)

        viewModel.refresh()
        runCurrent()
        viewModel.state.first { !it.isLoading }

        assertEquals(listOf(channel), viewModel.state.value.channels)
        assertEquals(listOf(program), viewModel.state.value.programs)
        assertEquals(mapOf(source.id to 1234L), viewModel.state.value.lastUpdatedEpochMillis)
        assertEquals("offline", viewModel.state.value.sourceErrors[source.id])
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun startingPlaybackPersistsChannelAsMostRecent() = runTest(mainDispatcher.dispatcher) {
        val sourceStore = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        val channelId = "source-1:channel"
        coEvery { sourceStore.list() } returns emptyList()
        coEvery { channelState.read() } returns LiveTvChannelState()
        coEvery { channelState.recordRecent(channelId) } returns LiveTvChannelState(recentChannelIds = listOf(channelId))
        val viewModel = LiveTvViewModel(sourceStore, importer, guideCache, channelState, refreshScheduler, activeProfiles())

        runCurrent()
        viewModel.recordRecent(channelId)
        runCurrent()

        assertEquals(listOf(channelId), viewModel.state.value.recentChannelIds)
        coVerify(exactly = 1) { channelState.recordRecent(channelId) }
    }

    @Test
    fun favoriteChangesArePersistedAndPublishedToScreenState() = runTest(mainDispatcher.dispatcher) {
        val sourceStore = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        val channelId = "source-1:channel"
        coEvery { sourceStore.list() } returns emptyList()
        coEvery { channelState.read() } returns LiveTvChannelState()
        coEvery { channelState.toggleFavorite(channelId) } returns LiveTvChannelState(favoriteChannelIds = listOf(channelId))
        val viewModel = LiveTvViewModel(sourceStore, importer, guideCache, channelState, refreshScheduler, activeProfiles())

        runCurrent()
        viewModel.toggleFavorite(channelId)
        runCurrent()

        assertEquals(setOf(channelId), viewModel.state.value.favoriteChannelIds)
        coVerify(exactly = 1) { channelState.toggleFavorite(channelId) }
    }

    @Test
    fun selectedTimeZoneIsPersistedAndPublishedToGuideState() = runTest(mainDispatcher.dispatcher) {
        val sourceStore = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        coEvery { sourceStore.list() } returns emptyList()
        coEvery { channelState.read() } returns LiveTvChannelState()
        coEvery { channelState.setTimeZone("America/Manaus") } returns LiveTvChannelState(timeZoneId = "America/Manaus")
        val viewModel = LiveTvViewModel(sourceStore, importer, guideCache, channelState, refreshScheduler, activeProfiles())

        runCurrent()
        viewModel.setTimeZone("America/Manaus")
        runCurrent()

        assertEquals("America/Manaus", viewModel.state.value.timeZoneId)
        coVerify(exactly = 1) { channelState.setTimeZone("America/Manaus") }
    }

    private fun activeProfiles() = mockk<ProfileManager> {
        every { activeProfileId } returns MutableStateFlow(1)
    }
}

