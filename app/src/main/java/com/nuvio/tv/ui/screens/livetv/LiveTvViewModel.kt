package com.nuvio.tv.ui.screens.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.livetv.LiveTvAddonCatalog
import com.nuvio.tv.core.livetv.installedLiveTvAddonCatalogs
import com.nuvio.tv.core.livetv.project
import com.nuvio.tv.core.livetv.LiveTvSourceImporter
import com.nuvio.tv.core.livetv.EpgShareRepository
import com.nuvio.tv.core.sports.SportsGame
import com.nuvio.tv.core.sports.SportsRepository
import com.nuvio.tv.core.livetv.LiveTvGuideCache
import com.nuvio.tv.core.livetv.LiveTvRefreshScheduler
import com.nuvio.tv.data.local.LiveTvSourceStore
import com.nuvio.tv.data.local.LiveTvChannelStateStore
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvProgram
import com.nuvio.tv.domain.model.LiveTvSource
import com.nuvio.tv.domain.model.supportsExtra
import com.nuvio.tv.domain.model.skipStep
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.StreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class LiveTvUiState(
    val sources: List<LiveTvSource> = emptyList(),
    val channels: List<LiveTvChannel> = emptyList(),
    val addonChannels: List<LiveTvChannel> = emptyList(),
    val isLoadingAddonChannels: Boolean = false,
    val addonCatalogCount: Int = 0,
    val addonCatalogErrorCount: Int = 0,
    val resolvingChannelIds: Set<String> = emptySet(),
    val programs: List<LiveTvProgram> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val backgroundRefreshUnavailable: Boolean = false,
    val sourceErrors: Map<String, String> = emptyMap(),
    val lastUpdatedEpochMillis: Map<String, Long> = emptyMap(),
    val lastSavedSourceId: String? = null,
    val favoriteChannelIds: Set<String> = emptySet(),
    val recentChannelIds: List<String> = emptyList(),
    val timeZoneId: String = LiveTvChannelStateStore.DEFAULT_TIME_ZONE_ID,
    /** Real schedule for add-on channels (epgshare01), keyed by channel id. */
    val guidePrograms: Map<String, List<LiveTvProgram>> = emptyMap(),
    val isLoadingGuide: Boolean = false,
    val guideMatchedChannels: Int = 0,
    val guideTotalChannels: Int = 0,
    val guideFailedRegions: List<String> = emptyList(),
    val epgRegions: List<String> = emptyList(),
    /** Today's games for the main leagues (ESPN scoreboard). */
    val games: List<SportsGame> = emptyList(),
    val isLoadingGames: Boolean = false,
    val gamesFailed: Boolean = false,
    val gamesUpdatedMillis: Long = 0L,
    /** 0 = today, -1 = yesterday, 1 = tomorrow. */
    val gamesDayOffset: Int = 0,
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val sourceStore: LiveTvSourceStore,
    private val importer: LiveTvSourceImporter,
    private val guideCache: LiveTvGuideCache,
    private val channelStateStore: LiveTvChannelStateStore,
    private val refreshScheduler: LiveTvRefreshScheduler,
    private val profileManager: ProfileManager,
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val streamRepository: StreamRepository,
    private val epgRepository: EpgShareRepository,
    private val sportsRepository: SportsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LiveTvUiState(isLoading = true))
    val state: StateFlow<LiveTvUiState> = mutableState.asStateFlow()
    private var installedAddons: List<Addon> = emptyList()
    private var guideJob: kotlinx.coroutines.Job? = null

    init {
        mutableState.update { it.copy(epgRegions = epgRepository.selectedRegions()) }
    }

    fun toggleEpgRegion(code: String) {
        val current = mutableState.value.epgRegions
        val next = if (code in current) current - code else current + code
        if (next.isEmpty()) return
        epgRepository.setRegions(next)
        mutableState.update { it.copy(epgRegions = epgRepository.selectedRegions()) }
        loadGuide()
    }

    private var gamesJob: kotlinx.coroutines.Job? = null
    private var loadingGamesDayOffset: Int? = null

    /** Fetches today's games; [force] skips the short in-memory cache (live scores). */
    fun setGamesDay(offset: Int) {
        if (offset == mutableState.value.gamesDayOffset) return
        mutableState.update { it.copy(gamesDayOffset = offset, games = emptyList(), gamesFailed = false) }
        loadGames()
    }

    fun loadGames(force: Boolean = false) {
        val offset = mutableState.value.gamesDayOffset
        if (!force && gamesJob?.isActive == true && loadingGamesDayOffset == offset) return
        gamesJob?.cancel()
        loadingGamesDayOffset = offset
        gamesJob = viewModelScope.launch {
            mutableState.update { it.copy(isLoadingGames = true) }
            try {
                val zone = mutableState.value.timeZoneId.let { id ->
                    if (id == LiveTvChannelStateStore.DEFAULT_TIME_ZONE_ID) java.time.ZoneId.systemDefault()
                    else runCatching { java.time.ZoneId.of(id) }.getOrDefault(java.time.ZoneId.systemDefault())
                }
                val games = sportsRepository.forDay(zone, offset, force)
                mutableState.update {
                    it.copy(games = games, isLoadingGames = false, gamesFailed = false, gamesUpdatedMillis = System.currentTimeMillis())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoadingGames = false, gamesFailed = it.games.isEmpty()) }
            }
        }
    }

    fun loadGuide() {
        guideJob?.cancel()
        val channels = mutableState.value.addonChannels
        if (channels.isEmpty()) {
            mutableState.update { it.copy(guidePrograms = emptyMap(), isLoadingGuide = false, guideMatchedChannels = 0, guideTotalChannels = 0) }
            return
        }
        guideJob = viewModelScope.launch {
            mutableState.update { it.copy(isLoadingGuide = true) }
            try {
                val result = epgRepository.load(channels, mutableState.value.epgRegions)
                mutableState.update {
                    it.copy(
                        guidePrograms = result.programsByChannelId,
                        guideMatchedChannels = result.matchedChannels,
                        guideTotalChannels = result.totalChannels,
                        guideFailedRegions = result.failedRegions,
                        isLoadingGuide = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoadingGuide = false) }
            }
        }
    }

    init {
        viewModelScope.launch {
            profileManager.activeProfileId.collectLatest { _ ->
                mutableState.update { it.copy(
                    sources = emptyList(), channels = emptyList(), programs = emptyList(),
                    addonChannels = emptyList(), addonCatalogCount = 0,
                    addonCatalogErrorCount = 0, isLoadingAddonChannels = true,
                    favoriteChannelIds = emptySet(), recentChannelIds = emptyList(),
                    sourceErrors = emptyMap(), lastUpdatedEpochMillis = emptyMap(),
                    isLoading = true, error = null, backgroundRefreshUnavailable = false,
                ) }
                reloadSources()
                try {
                    val saved = channelStateStore.read()
                    mutableState.update { it.copy(
                        favoriteChannelIds = saved.favoriteChannelIds.toSet(),
                        recentChannelIds = saved.recentChannelIds,
                        timeZoneId = saved.timeZoneId,
                    ) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the screen usable if optional local channel preferences are unreadable.
                }
                updateScheduledRefresh()
            }
        }
        viewModelScope.launch {
            addonRepository.getInstalledAddons().collectLatest { addons ->
                installedAddons = addons
                loadAddonChannels(addons)
            }
        }
    }

    private suspend fun loadAddonChannels(addons: List<Addon>) {
        val catalogs = installedLiveTvAddonCatalogs(addons)
        if (catalogs.isEmpty()) {
            mutableState.update {
                it.copy(
                    addonChannels = emptyList(),
                    addonCatalogCount = 0,
                    addonCatalogErrorCount = 0,
                    isLoadingAddonChannels = false,
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                isLoadingAddonChannels = true,
                addonCatalogCount = catalogs.size,
                addonCatalogErrorCount = 0,
            )
        }

        val semaphore = Semaphore(MAX_PARALLEL_TV_ADDON_CATALOGS)
        val results = arrayOfNulls<AddonCatalogLoad>(catalogs.size)
        coroutineScope {
            catalogs.mapIndexed { index, request ->
                async {
                    val result = semaphore.withPermit { loadAddonCatalog(request) { partial ->
                        results[index] = AddonCatalogLoad(partial, failed = false)
                        publishAddonCatalogResults(results)
                    } }
                    results[index] = result
                    publishAddonCatalogResults(results)
                }
            }.awaitAll()
        }
        mutableState.update {
            it.copy(
                addonCatalogCount = catalogs.size,
                isLoadingAddonChannels = false,
            )
        }
        loadGuide()
    }

    private fun publishAddonCatalogResults(results: Array<AddonCatalogLoad?>) {
        mutableState.update {
            it.copy(
                addonChannels = results.filterNotNull().flatMap(AddonCatalogLoad::channels)
                    .distinctBy(::addonChannelIdentity),
                addonCatalogErrorCount = results.filterNotNull().count(AddonCatalogLoad::failed),
            )
        }
    }

    /** One quiet retry: an add-on that is still waking up often answers the second request. */
    private suspend fun loadAddonCatalog(request: LiveTvAddonCatalog, onPage: (List<LiveTvChannel>) -> Unit): AddonCatalogLoad {
        val first = loadAddonCatalogOnce(request, onPage)
        if (!first.failed || first.channels.isNotEmpty()) return first
        kotlinx.coroutines.delay(1_500L)
        return loadAddonCatalogOnce(request, onPage)
    }

    private suspend fun loadAddonCatalogOnce(request: LiveTvAddonCatalog, onPage: (List<LiveTvChannel>) -> Unit): AddonCatalogLoad {
        val channels = linkedMapOf<String, LiveTvChannel>()
        val supportsSkip = request.catalog.supportsExtra("skip")
        var skip = 0
        return try {
            repeat(MAX_TV_CATALOG_PAGES) { page ->
                val result = catalogRepository.getCatalog(
                addonBaseUrl = request.addon.baseUrl,
                addonId = request.addon.id,
                addonName = request.addon.displayName,
                catalogId = request.catalog.id,
                catalogName = request.catalog.name,
                type = request.catalog.apiType,
                skip = skip,
                skipStep = request.catalog.skipStep(),
                supportsSkip = supportsSkip,
            ).first { it !is NetworkResult.Loading }
                when (result) {
                    is NetworkResult.Success -> {
                        val projected = request.project(result.data.items)
                        val previousSize = channels.size
                        projected.forEach { channels.putIfAbsent(it.id, it) }
                        if (channels.size > previousSize) onPage(channels.values.toList())
                        val nextSkip = result.data.nextSkip
                        if (!supportsSkip || projected.isEmpty() || channels.size == previousSize ||
                            !result.data.hasMore || nextSkip <= skip) return AddonCatalogLoad(channels.values.toList(), false)
                        skip = nextSkip
                    }
                    is NetworkResult.Error, NetworkResult.Loading -> return AddonCatalogLoad(channels.values.toList(), true)
                }
            }
            AddonCatalogLoad(channels.values.toList(), failed = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Addon errors are isolated and URLs may contain private config tokens.
            AddonCatalogLoad(channels.values.toList(), failed = true)
        }
    }

    fun playChannel(channel: LiveTvChannel, onPlay: (LiveTvChannel) -> Unit) {
        if (!channel.streamUrl.isNullOrBlank()) {
            recordRecent(channel.id)
            onPlay(channel)
            return
        }

        val addon = installedAddons.firstOrNull {
            it.enabled && it.id == channel.addonId && it.baseUrl == channel.addonBaseUrl
        }
        val type = channel.addonType
        val contentId = channel.addonContentId
        if (addon == null || type.isNullOrBlank() || contentId.isNullOrBlank()) {
            mutableState.update { it.copy(error = "Este canal não está mais disponível no addon instalado.") }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(
                resolvingChannelIds = it.resolvingChannelIds + channel.id,
                error = null,
            ) }
            try {
                val result = streamRepository.getStreamsFromAddon(addon, type, contentId)
                val stream = (result as? NetworkResult.Success)
                    ?.data
                    ?.firstOrNull { candidate -> !candidate.getStreamUrl().isNullOrBlank() }
                val streamUrl = stream?.getStreamUrl()?.takeIf(String::isNotBlank)
                if (stream == null || streamUrl == null) {
                    mutableState.update { it.copy(error = "O addon não retornou uma transmissão reproduzível.") }
                    return@launch
                }
                recordRecent(channel.id)
                onPlay(channel.copy(streamUrl = streamUrl, addonDisplayName = stream.addonName))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(error = "Não foi possível abrir este canal do addon.") }
            } finally {
                mutableState.update { it.copy(resolvingChannelIds = it.resolvingChannelIds - channel.id) }
            }
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            try {
                val saved = channelStateStore.toggleFavorite(channelId)
                mutableState.update { it.copy(
                    favoriteChannelIds = saved.favoriteChannelIds.toSet(),
                    recentChannelIds = saved.recentChannelIds,
                    error = null,
                ) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = failure.message ?: "Não foi possível salvar o favorito") }
            }
        }
    }

    fun recordRecent(channelId: String) {
        viewModelScope.launch {
            try {
                val saved = channelStateStore.recordRecent(channelId)
                mutableState.update { it.copy(recentChannelIds = saved.recentChannelIds, error = null) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = failure.message ?: "Não foi possível atualizar os canais recentes") }
            }
        }
    }

    fun setTimeZone(timeZoneId: String) {
        viewModelScope.launch {
            try {
                val saved = channelStateStore.setTimeZone(timeZoneId)
                mutableState.update { it.copy(timeZoneId = saved.timeZoneId, error = null) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = failure.message ?: "Não foi possível salvar o fuso horário") }
            }
        }
    }

    private fun reconcileRefreshSchedule() {
        viewModelScope.launch { updateScheduledRefresh() }
    }

    private suspend fun updateScheduledRefresh() {
        val hasEnabledSource = try {
            sourceStore.list().any { it.enabled }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        applyRefreshSchedule(hasEnabledSource)
    }

    private fun applyRefreshSchedule(enabled: Boolean) {
        try {
            if (enabled) refreshScheduler.schedule() else refreshScheduler.cancel()
            mutableState.update { it.copy(backgroundRefreshUnavailable = false) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(backgroundRefreshUnavailable = true) }
        }
    }

    private suspend fun reloadSources() {
        try {
            val sources = sourceStore.list()
            val cached = sources.filter { it.enabled }.mapNotNull { source ->
                try {
                    guideCache.read(source.id)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            mutableState.update { it.copy(
                sources = sources,
                channels = cached.flatMap { it.result.channels }.distinctBy { channel -> channel.id },
                programs = cached.flatMap { it.result.programs }
                    .distinctBy { program -> "${program.sourceId}|${program.channelId}|${program.startEpochMillis}|${program.title}" },
                lastUpdatedEpochMillis = cached.associate { it.sourceId to it.updatedEpochMillis },
                isLoading = false,
            ) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutableState.update { it.copy(isLoading = false, error = failure.message ?: "Falha ao carregar fontes") }
        }
    }

    fun addSource(name: String, playlistUrl: String, epgUrl: String?) {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            runCatching { sourceStore.add(name, playlistUrl, epgUrl) }
                .onSuccess { added ->
                    mutableState.update { it.copy(sources = it.sources + added, isLoading = false, lastSavedSourceId = added.id) }
                    applyRefreshSchedule(enabled = true)
                    refresh()
                }
                .onFailure { error -> mutableState.update { it.copy(isLoading = false, error = error.message ?: "Não foi possível salvar a fonte") } }
        }
    }

    fun setSourceEnabled(source: LiveTvSource, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { sourceStore.setEnabled(source.id, enabled) }
                .onSuccess {
                    mutableState.update { state ->
                        state.copy(
                            sources = state.sources.map { if (it.id == source.id) it.copy(enabled = enabled) else it },
                            channels = if (enabled) state.channels else state.channels.filterNot { it.sourceId == source.id },
                            programs = if (enabled) state.programs else state.programs.filterNot { it.sourceId == source.id },
                        )
                    }
                    if (enabled) {
                        applyRefreshSchedule(enabled = true)
                        refresh()
                    } else {
                        reconcileRefreshSchedule()
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(error = error.message ?: "Não foi possível atualizar a fonte") } }
        }
    }

    fun removeSource(source: LiveTvSource) {
        viewModelScope.launch {
            runCatching {
                sourceStore.remove(source.id)
                guideCache.remove(source.id)
                channelStateStore.removeSource(source.id)
            }
                .onSuccess { channelState ->
                    mutableState.update { state -> state.copy(sources = state.sources.filterNot { it.id == source.id },
                        channels = state.channels.filterNot { it.sourceId == source.id },
                        programs = state.programs.filterNot { it.sourceId == source.id },
                        favoriteChannelIds = channelState.favoriteChannelIds.toSet(),
                        recentChannelIds = channelState.recentChannelIds,
                        sourceErrors = state.sourceErrors - source.id,
                        lastUpdatedEpochMillis = state.lastUpdatedEpochMillis - source.id) }
                    reconcileRefreshSchedule()
                }
                .onFailure { error -> mutableState.update { it.copy(error = error.message ?: "Não foi possível remover a fonte") } }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val sources = mutableState.value.sources.filter { it.enabled }
            if (sources.isEmpty()) {
                mutableState.update { it.copy(channels = emptyList(), programs = emptyList(), sourceErrors = emptyMap(), lastUpdatedEpochMillis = emptyMap(), error = null) }
                return@launch
            }
            mutableState.update { it.copy(isLoading = true, error = null, sourceErrors = emptyMap()) }
            val resultBySource = linkedMapOf<String, com.nuvio.tv.domain.model.LiveTvImportResult>()
            val updatedBySource = linkedMapOf<String, Long>()
            for (source in sources) {
                val cached = try {
                    guideCache.read(source.id)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                cached?.let {
                    resultBySource[source.id] = it.result
                    updatedBySource[source.id] = it.updatedEpochMillis
                }
            }
            mutableState.update { state ->
                state.copy(
                    channels = resultBySource.values.flatMap { it.channels }.distinctBy(LiveTvChannel::id),
                    programs = resultBySource.values.flatMap { it.programs }.distinctBy { "${it.sourceId}|${it.channelId}|${it.startEpochMillis}|${it.title}" },
                )
            }
            val errors = linkedMapOf<String, String>()
            for (source in sources) {
                try {
                    val result = importer.import(source)
                    resultBySource[source.id] = result
                    guideCache.write(source.id, result)
                    updatedBySource[source.id] = System.currentTimeMillis()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    errors[source.id] = failure.message ?: "Falha ao importar a fonte"
                }
            }
            val channels = resultBySource.values.flatMap { it.channels }
            mutableState.update { it.copy(
                channels = channels.distinctBy(LiveTvChannel::id),
                programs = resultBySource.values.flatMap { it.programs }
                    .distinctBy { "${it.sourceId}|${it.channelId}|${it.startEpochMillis}|${it.title}" },
                lastUpdatedEpochMillis = updatedBySource,
                sourceErrors = errors,
                isLoading = false,
                error = if (channels.isEmpty() && errors.isNotEmpty()) "Não foi possível importar canais das fontes ativas" else null,
            ) }
        }
    }
}

private data class AddonCatalogLoad(
    val channels: List<LiveTvChannel>,
    val failed: Boolean,
)

private const val MAX_TV_CATALOG_PAGES = 100
private const val MAX_PARALLEL_TV_ADDON_CATALOGS = 3

private fun addonChannelIdentity(channel: LiveTvChannel): String =
    listOf(channel.addonId, channel.addonBaseUrl, channel.displayName.trim().lowercase().replace(Regex("\\s+"), " "))
        .joinToString("|")

