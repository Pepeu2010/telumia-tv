package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.tracking.TrackingProviderId
import com.nuvio.tv.core.tracking.TrackingSourceController
import com.nuvio.tv.core.tracking.TrackingSourceSelection
import com.nuvio.tv.core.tracking.availableLibrarySourceModes
import com.nuvio.tv.core.tracking.availableWatchProgressSources
import com.nuvio.tv.core.tracking.effectiveTrackingSourceSelection
import com.nuvio.tv.data.local.MoreLikeThisSourcePreference
import com.nuvio.tv.data.local.TrackingSettingsDataStore
import com.nuvio.tv.data.local.WatchProgressSource
import com.nuvio.tv.data.simkl.SimklAnimeIdPreference
import com.nuvio.tv.data.simkl.SimklAuthRepository
import com.nuvio.tv.data.simkl.SimklSyncRepository
import com.nuvio.tv.data.mdblist.MdbListAuthStore
import com.nuvio.tv.domain.model.LibrarySourceMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrackingSettingsUiState(
    val watchProgressSource: WatchProgressSource = WatchProgressSource.NUVIO_SYNC,
    val librarySourceMode: LibrarySourceMode = LibrarySourceMode.LOCAL,
    val connectedProviderIds: Set<TrackingProviderId> = emptySet(),
    val simklAnimeIdPreference: SimklAnimeIdPreference = SimklAnimeIdPreference.DEFAULT,
    val continueWatchingDaysCap: Int = TrackingSettingsDataStore.DEFAULT_CONTINUE_WATCHING_DAYS_CAP,
    val moreLikeThisSource: MoreLikeThisSourcePreference = TrackingSettingsDataStore.DEFAULT_MORE_LIKE_THIS_SOURCE,
    val isReady: Boolean = false
) {
    val availableWatchProgressSources: List<WatchProgressSource>
        get() = availableWatchProgressSources(connectedProviderIds)

    val availableLibrarySourceModes: List<LibrarySourceMode>
        get() = availableLibrarySourceModes(connectedProviderIds)
}

@HiltViewModel
class TrackingSettingsViewModel @Inject constructor(
    private val sourceController: TrackingSourceController,
    private val settingsDataStore: TrackingSettingsDataStore,
    private val simklSyncRepository: SimklSyncRepository,
    simklAuthRepository: SimklAuthRepository,
    mdbListAuth: MdbListAuthStore,
    profiles: ProfileManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrackingSettingsUiState())
    val uiState: StateFlow<TrackingSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val connected = combine(simklAuthRepository.state, mdbListAuth.state, profiles.activeProfileId) { simkl, mdblist, profileId ->
                if (mdblist.scope.profileId != profileId) null else buildSet<TrackingProviderId> {
                    if (simkl.isAuthenticated) add(TrackingProviderId.SIMKL)
                    if (mdblist.isAuthenticated) add(TrackingProviderId.MDBLIST)
                }
            }
            combine(
                sourceController.watchProgressSource,
                sourceController.librarySourceMode,
                connected,
                combine(
                    settingsDataStore.simklAnimeIdPreference,
                    settingsDataStore.continueWatchingDaysCap,
                    settingsDataStore.moreLikeThisSource
                ) { animeIdPref, daysCap, moreLikeThis -> Triple(animeIdPref, daysCap, moreLikeThis) }
            ) { watchProgressSource, librarySourceMode, connectedProviderIds, preferences ->
                val (animeIdPref, daysCap, moreLikeThis) = preferences
                if (connectedProviderIds == null) return@combine TrackingSettingsUiState()
                val effective = effectiveTrackingSourceSelection(
                    requested = TrackingSourceSelection(watchProgressSource, librarySourceMode),
                    connectedProviderIds = connectedProviderIds
                )
                TrackingSettingsUiState(
                    watchProgressSource = effective.watchProgressSource,
                    librarySourceMode = effective.librarySourceMode,
                    connectedProviderIds = connectedProviderIds,
                    simklAnimeIdPreference = animeIdPref,
                    continueWatchingDaysCap = daysCap,
                    moreLikeThisSource = moreLikeThis,
                    isReady = true
                )
            }.collect { state ->
                _uiState.value = state
                if (state.isReady && mdbListAuth.scope().profileId == profiles.activeProfileId.value) {
                    sourceController.reconcileConnectedProviders(state.connectedProviderIds)
                }
            }
        }
    }

    fun selectWatchProgressSource(source: WatchProgressSource) {
        viewModelScope.launch {
            sourceController.selectWatchProgressSource(source)
        }
    }

    fun selectLibrarySourceMode(mode: LibrarySourceMode) {
        viewModelScope.launch {
            sourceController.selectLibrarySourceMode(mode)
        }
    }

    fun selectContinueWatchingDaysCap(days: Int) {
        viewModelScope.launch { settingsDataStore.setContinueWatchingDaysCap(days) }
    }

    fun selectMoreLikeThisSource(source: MoreLikeThisSourcePreference) {
        viewModelScope.launch { settingsDataStore.setMoreLikeThisSource(source) }
    }

    fun selectSimklAnimeIdPreference(preference: SimklAnimeIdPreference) {
        viewModelScope.launch {
            settingsDataStore.setSimklAnimeIdPreference(preference)
            simklSyncRepository.invalidateProjections(preference)
        }
    }
}
