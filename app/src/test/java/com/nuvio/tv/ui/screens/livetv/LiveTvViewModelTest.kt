package com.nuvio.tv.ui.screens.livetv

import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.livetv.LiveTvRefreshScheduler
import com.nuvio.tv.core.livetv.LiveTvSourceImporter
import com.nuvio.tv.core.livetv.LiveTvGuideCache
import com.nuvio.tv.core.livetv.CachedLiveTvGuide
import com.nuvio.tv.core.livetv.project
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.local.LiveTvSourceStore
import com.nuvio.tv.data.local.LiveTvChannelState
import com.nuvio.tv.data.local.LiveTvChannelStateStore
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvImportResult
import com.nuvio.tv.domain.model.LiveTvSource
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.StreamRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun enabledAddonChannelCatalogsLoadIntoLiveTvAndMovieCatalogsAreIgnored() = runTest(mainDispatcher.dispatcher) {
        val sourceStore = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        val catalogRepository = mockk<CatalogRepository>()
        val addon = testAddon(
            catalogs = listOf(
                CatalogDescriptor(ContentType.CHANNEL, "channel", "channels", "Channels"),
                CatalogDescriptor(ContentType.TV, "tv", "live", "Live"),
                CatalogDescriptor(ContentType.MOVIE, "movie", "movies", "Movies"),
            ),
            rawTypes = listOf("channel", "tv", "movie"),
        )
        val requestedTypes = mutableListOf<String>()
        coEvery { sourceStore.list() } returns emptyList()
        coEvery { channelState.read() } returns LiveTvChannelState()
        every {
            catalogRepository.getCatalog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            val type = arg<String>(5)
            requestedTypes += type
            val catalog = addon.catalogs.single { it.apiType == type }
            val row = CatalogRow(
                addonId = addon.id,
                addonName = addon.displayName,
                addonBaseUrl = addon.baseUrl,
                catalogId = catalog.id,
                catalogName = catalog.name,
                type = catalog.type,
                rawType = catalog.apiType,
                items = listOf(testPreview("$type:band", "${catalog.name} channel", type)),
                hasMore = false,
            )
            flowOf(NetworkResult.Loading, NetworkResult.Success(row))
        }
        val viewModel = newViewModel(
            sourceStore, importer, guideCache, channelState, refreshScheduler,
            addonFlow = MutableStateFlow(listOf(addon)),
            catalogRepository = catalogRepository,
        )

        runCurrent()

        assertEquals(listOf("channel", "tv"), requestedTypes.sorted())
        assertEquals(2, viewModel.state.value.addonCatalogCount)
        assertEquals(0, viewModel.state.value.addonCatalogErrorCount)
        assertFalse(viewModel.state.value.isLoadingAddonChannels)
        assertEquals(2, viewModel.state.value.addonChannels.size)
        assertTrue(viewModel.state.value.addonChannels.all(LiveTvChannel::isPlayable))
        assertTrue(viewModel.state.value.channels.isEmpty())
    }

    @Test
    fun offlineAddonCatalogFailureLeavesLiveTvUsableAndReportsFailure() = runTest(mainDispatcher.dispatcher) {
        val sourceStore = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        val catalogRepository = mockk<CatalogRepository>()
        val addon = testAddon(
            catalogs = listOf(CatalogDescriptor(ContentType.CHANNEL, "channel", "channels", "Channels")),
        )
        coEvery { sourceStore.list() } returns emptyList()
        coEvery { channelState.read() } returns LiveTvChannelState()
        every {
            catalogRepository.getCatalog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns flowOf(NetworkResult.Loading, NetworkResult.Error("offline", 503))
        val viewModel = newViewModel(
            sourceStore, importer, guideCache, channelState, refreshScheduler,
            addonFlow = MutableStateFlow(listOf(addon)),
            catalogRepository = catalogRepository,
        )

        runCurrent()

        assertEquals(1, viewModel.state.value.addonCatalogCount)
        assertEquals(1, viewModel.state.value.addonCatalogErrorCount)
        assertFalse(viewModel.state.value.isLoadingAddonChannels)
        assertTrue(viewModel.state.value.addonChannels.isEmpty())
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun addonChannelPlaybackResolvesAPlayableStreamAndRecordsRecentChannel() = runTest(mainDispatcher.dispatcher) {
        val sourceStore = mockk<LiveTvSourceStore>()
        val importer = mockk<LiveTvSourceImporter>()
        val guideCache = mockk<LiveTvGuideCache>()
        val channelState = mockk<LiveTvChannelStateStore>()
        val refreshScheduler = mockk<LiveTvRefreshScheduler>(relaxed = true)
        val catalogRepository = mockk<CatalogRepository>()
        val streamRepository = mockk<StreamRepository>()
        val catalog = CatalogDescriptor(ContentType.CHANNEL, "channel", "channels", "Channels")
        val addon = testAddon(catalogs = listOf(catalog))
        val channel = testPreview("br:band", "Band", "channel")
        val catalogRow = CatalogRow(
            addonId = addon.id,
            addonName = addon.displayName,
            addonBaseUrl = addon.baseUrl,
            catalogId = catalog.id,
            catalogName = catalog.name,
            type = catalog.type,
            rawType = catalog.apiType,
            items = listOf(channel),
            hasMore = false,
        )
        val projectedChannel = com.nuvio.tv.core.livetv.installedLiveTvAddonCatalogs(listOf(addon))
            .single().project(listOf(channel)).single()
        val streamUrl = "https://stream.example/live.m3u8"
        coEvery { sourceStore.list() } returns emptyList()
        coEvery { channelState.read() } returns LiveTvChannelState()
        coEvery { channelState.recordRecent(projectedChannel.id) } returns
            LiveTvChannelState(recentChannelIds = listOf(projectedChannel.id))
        every {
            catalogRepository.getCatalog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns flowOf(NetworkResult.Success(catalogRow))
        coEvery { streamRepository.getStreamsFromAddon(addon, "channel", "br:band") } returns
            NetworkResult.Success(listOf(testStream(streamUrl, addon.displayName)))
        val viewModel = newViewModel(
            sourceStore, importer, guideCache, channelState, refreshScheduler,
            addonFlow = MutableStateFlow(listOf(addon)),
            catalogRepository = catalogRepository,
            streamRepository = streamRepository,
        )
        var openedChannel: LiveTvChannel? = null

        runCurrent()
        viewModel.playChannel(projectedChannel) { openedChannel = it }
        runCurrent()

        assertEquals(streamUrl, openedChannel?.streamUrl)
        assertEquals(addon.displayName, openedChannel?.addonDisplayName)
        assertEquals(listOf(projectedChannel.id), viewModel.state.value.recentChannelIds)
        coVerify(exactly = 1) { streamRepository.getStreamsFromAddon(addon, "channel", "br:band") }
    }

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
        val viewModel = newViewModel(store, importer, guideCache, channelState, refreshScheduler)

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
        val viewModel = newViewModel(store, importer, guideCache, channelState, refreshScheduler)

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
        val viewModel = newViewModel(store, importer, guideCache, channelState, refreshScheduler)

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
        val viewModel = newViewModel(store, importer, guideCache, channelState, refreshScheduler)

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
        val viewModel = newViewModel(sourceStore, importer, guideCache, channelState, refreshScheduler)

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
        val viewModel = newViewModel(sourceStore, importer, guideCache, channelState, refreshScheduler)

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
        val viewModel = newViewModel(sourceStore, importer, guideCache, channelState, refreshScheduler)

        runCurrent()
        viewModel.setTimeZone("America/Manaus")
        runCurrent()

        assertEquals("America/Manaus", viewModel.state.value.timeZoneId)
        coVerify(exactly = 1) { channelState.setTimeZone("America/Manaus") }
    }

    private fun activeProfiles() = mockk<ProfileManager> {
        every { activeProfileId } returns MutableStateFlow(1)
    }

    private fun newViewModel(
        store: LiveTvSourceStore,
        importer: LiveTvSourceImporter,
        guideCache: LiveTvGuideCache,
        channelState: LiveTvChannelStateStore,
        refreshScheduler: LiveTvRefreshScheduler,
        addonFlow: Flow<List<Addon>> = MutableStateFlow(emptyList()),
        catalogRepository: CatalogRepository = mockk(relaxed = true),
        streamRepository: StreamRepository = mockk(relaxed = true),
    ) = LiveTvViewModel(
        sourceStore = store,
        importer = importer,
        guideCache = guideCache,
        channelStateStore = channelState,
        refreshScheduler = refreshScheduler,
        profileManager = activeProfiles(),
        addonRepository = mockk<AddonRepository> {
            every { getInstalledAddons() } returns addonFlow
        },
        catalogRepository = catalogRepository,
        streamRepository = streamRepository,
        sportsRepository = mockk<com.nuvio.tv.core.sports.SportsRepository>(relaxed = true),
        epgRepository = mockk<com.nuvio.tv.core.livetv.EpgShareRepository> {
            every { selectedRegions() } returns listOf("BR1")
            coEvery { load(any(), any()) } returns com.nuvio.tv.core.livetv.EpgGuideResult(emptyMap(), 0, 0, emptyList())
        },
    )

    private fun testAddon(
        id: String = "org.test.channels",
        catalogs: List<CatalogDescriptor>,
        rawTypes: List<String> = catalogs.map(CatalogDescriptor::apiType),
    ) = Addon(
        id = id,
        name = "Test addon",
        displayName = "Test addon",
        version = "1.0.0",
        description = null,
        logo = null,
        baseUrl = "https://addons.example/$id/manifest.json",
        catalogs = catalogs,
        types = rawTypes.map(ContentType::fromString),
        rawTypes = rawTypes,
        resources = listOf(AddonResource("stream", rawTypes, null)),
    )

    private fun testPreview(id: String, name: String, type: String) = MetaPreview(
        id = id,
        type = ContentType.fromString(type),
        rawType = type,
        name = name,
        poster = null,
        posterShape = PosterShape.SQUARE,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList(),
    )

    private fun testStream(url: String, addonName: String) = Stream(
        name = null,
        title = null,
        description = null,
        url = url,
        ytId = null,
        infoHash = null,
        fileIdx = null,
        externalUrl = null,
        behaviorHints = null,
        addonName = addonName,
        addonLogo = null,
    )
}

