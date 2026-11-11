@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.TrackingProviderId
import com.nuvio.tv.data.local.MoreLikeThisSourcePreference
import com.nuvio.tv.data.local.TrackingSettingsDataStore
import com.nuvio.tv.data.local.WatchProgressSource
import com.nuvio.tv.data.simkl.SimklAnimeIdPreference
import com.nuvio.tv.data.simkl.SimklConnectionMode
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

@Composable
fun TrackingSettingsScreen(
    simklViewModel: SimklSettingsViewModel = hiltViewModel(),
    mdbListViewModel: MdbListTrackerViewModel = hiltViewModel(),
    trackingViewModel: TrackingSettingsViewModel = hiltViewModel(),
    onBackPress: () -> Unit
) {
    val simklState by simklViewModel.uiState.collectAsStateWithLifecycle()
    val mdbListState by mdbListViewModel.uiState.collectAsStateWithLifecycle()
    val mdbListLibraryLists by mdbListViewModel.libraryLists.collectAsStateWithLifecycle()
    val trackingState by trackingViewModel.uiState.collectAsStateWithLifecycle()
    val simklFocusRequester = remember { FocusRequester() }
    val mdbListFocusRequester = remember { FocusRequester() }
    val libraryFocusRequester = remember { FocusRequester() }
    val watchProgressFocusRequester = remember { FocusRequester() }
    val continueWatchingFocusRequester = remember { FocusRequester() }
    val moreLikeThisFocusRequester = remember { FocusRequester() }

    var activeProvider by remember { mutableStateOf<TrackingProviderId?>(null) }
    var dismissOnConnected by remember { mutableStateOf<TrackingProviderId?>(null) }
    var disconnectProvider by remember { mutableStateOf<TrackingProviderId?>(null) }
    var restoreFocusTarget by remember { mutableStateOf<TrackingFocusTarget?>(null) }
    var showLibrarySourceDialog by remember { mutableStateOf(false) }
    var showWatchProgressDialog by remember { mutableStateOf(false) }
    var showDaysCapDialog by remember { mutableStateOf(false) }
    var showMoreLikeThisSourceDialog by remember { mutableStateOf(false) }
    var showAnimeIdDialog by remember { mutableStateOf(false) }
    var showMdbListLibraryListsDialog by remember { mutableStateOf(false) }

    val hasOverlay = activeProvider != null ||
        disconnectProvider != null ||
        showLibrarySourceDialog ||
        showWatchProgressDialog ||
        showDaysCapDialog ||
        showMoreLikeThisSourceDialog ||
        showAnimeIdDialog ||
        showMdbListLibraryListsDialog

    BackHandler(enabled = !hasOverlay) {
        onBackPress()
    }

    LaunchedEffect(Unit) {
        delay(160L)
        runCatching { simklFocusRequester.requestFocus() }
    }

    LaunchedEffect(
        activeProvider,
        dismissOnConnected,
        simklState.mode,
        mdbListState.isConnected
    ) {
        val connected = when (dismissOnConnected) {
            TrackingProviderId.SIMKL -> simklState.mode == SimklConnectionMode.CONNECTED
            TrackingProviderId.MDBLIST -> mdbListState.isConnected
            null -> false
        }
        if (activeProvider == dismissOnConnected && connected) {
            activeProvider = null
            dismissOnConnected = null
        }
    }

    LaunchedEffect(hasOverlay, restoreFocusTarget) {
        val target = restoreFocusTarget ?: return@LaunchedEffect
        if (hasOverlay) return@LaunchedEffect
        delay(120L)
        runCatching {
            when (target) {
                TrackingFocusTarget.SIMKL -> simklFocusRequester.requestFocus()
                TrackingFocusTarget.MDBLIST -> mdbListFocusRequester.requestFocus()
                TrackingFocusTarget.LIBRARY -> libraryFocusRequester.requestFocus()
                TrackingFocusTarget.WATCH_PROGRESS -> watchProgressFocusRequester.requestFocus()
                TrackingFocusTarget.CONTINUE_WATCHING -> continueWatchingFocusRequester.requestFocus()
                TrackingFocusTarget.MORE_LIKE_THIS -> moreLikeThisFocusRequester.requestFocus()
            }
        }
        restoreFocusTarget = null
    }

    val openProvider: (TrackingProviderId) -> Unit = { provider ->
        restoreFocusTarget = when (provider) {
            TrackingProviderId.SIMKL -> TrackingFocusTarget.SIMKL
            TrackingProviderId.MDBLIST -> TrackingFocusTarget.MDBLIST
        }
        activeProvider = provider
        disconnectProvider = null
        when (provider) {
            TrackingProviderId.SIMKL -> {
                if (simklState.mode == SimklConnectionMode.CONNECTED) {
                    dismissOnConnected = null
                } else {
                    dismissOnConnected = provider
                    if (simklState.mode == SimklConnectionMode.DISCONNECTED && !simklState.isLoading) {
                        simklViewModel.onConnect()
                    }
                }
            }
            TrackingProviderId.MDBLIST -> {
                if (mdbListState.isConnected) {
                    dismissOnConnected = null
                    mdbListViewModel.onAccountOpened()
                } else {
                    dismissOnConnected = provider
                    mdbListViewModel.onConnect()
                }
            }
        }
    }

    TrackingSettingsOverview(
        simklState = simklState,
        mdbListState = mdbListState,
        trackingState = trackingState,
        simklFocusRequester = simklFocusRequester,
        mdbListFocusRequester = mdbListFocusRequester,
        libraryFocusRequester = libraryFocusRequester,
        watchProgressFocusRequester = watchProgressFocusRequester,
        continueWatchingFocusRequester = continueWatchingFocusRequester,
        moreLikeThisFocusRequester = moreLikeThisFocusRequester,
        onSimklClick = { openProvider(TrackingProviderId.SIMKL) },
        onMdbListClick = { openProvider(TrackingProviderId.MDBLIST) },
        onLibrarySourceClick = {
            restoreFocusTarget = TrackingFocusTarget.LIBRARY
            showLibrarySourceDialog = true
        },
        onWatchProgressClick = {
            restoreFocusTarget = TrackingFocusTarget.WATCH_PROGRESS
            showWatchProgressDialog = true
        },
        onContinueWatchingWindowClick = {
            restoreFocusTarget = TrackingFocusTarget.CONTINUE_WATCHING
            showDaysCapDialog = true
        },
        onMoreLikeThisClick = {
            restoreFocusTarget = TrackingFocusTarget.MORE_LIKE_THIS
            showMoreLikeThisSourceDialog = true
        },
        onAnimeIdClick = {
            showAnimeIdDialog = true
        },
        mdbListLibraryLists = mdbListLibraryLists,
        onMdbListLibraryListsClick = {
            mdbListViewModel.onLibraryListsOpened()
            showMdbListLibraryListsDialog = true
        }
    )

    when (activeProvider) {
        TrackingProviderId.SIMKL -> {
            SimklAccountDialog(
                state = simklState,
                onStartConnection = simklViewModel::onConnect,
                onRetryPolling = simklViewModel::onRetryPolling,
                onSync = simklViewModel::onSyncNow,
                onDisconnect = {
                    activeProvider = null
                    dismissOnConnected = null
                    disconnectProvider = TrackingProviderId.SIMKL
                },
                onDismiss = {
                    if (simklState.mode != SimklConnectionMode.CONNECTED) {
                        simklViewModel.onCancel()
                    }
                    activeProvider = null
                    dismissOnConnected = null
                }
            )
        }
        TrackingProviderId.MDBLIST -> {
            MdbListAccountDialog(
                state = mdbListState,
                onStartConnection = mdbListViewModel::onConnect,
                onRetryPolling = mdbListViewModel::onRetryPolling,
                onSync = mdbListViewModel::onSyncNow,
                onDisconnect = {
                    activeProvider = null
                    dismissOnConnected = null
                    disconnectProvider = TrackingProviderId.MDBLIST
                },
                onDismiss = {
                    if (!mdbListState.isConnected) mdbListViewModel.onCancel()
                    activeProvider = null
                    dismissOnConnected = null
                }
            )
        }
        null -> Unit
    }

    disconnectProvider?.let { provider ->
        NuvioDialog(
            onDismiss = {
                disconnectProvider = null
                activeProvider = provider
            },
            title = stringResource(
                when (provider) {
                    TrackingProviderId.SIMKL -> R.string.simkl_disconnect_title
                    TrackingProviderId.MDBLIST -> R.string.mdblist_disconnect_title
                }
            ),
            subtitle = stringResource(
                when (provider) {
                    TrackingProviderId.SIMKL -> R.string.simkl_disconnect_subtitle
                    TrackingProviderId.MDBLIST -> R.string.mdblist_disconnect_subtitle
                }
            ),
            width = 520.dp,
            suppressFirstKeyUp = false
        ) {
            SettingsDialogActionRow {
                SettingsDialogActionButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = {
                        disconnectProvider = null
                        activeProvider = provider
                    }
                )
                SettingsDialogActionButton(
                    text = stringResource(R.string.tracking_disconnect),
                    onClick = {
                        disconnectProvider = null
                        when (provider) {
                            TrackingProviderId.SIMKL -> simklViewModel.onDisconnect()
                            TrackingProviderId.MDBLIST -> mdbListViewModel.onDisconnect()
                        }
                    },
                    primary = true
                )
            }
        }
    }

    if (showLibrarySourceDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.tracking_library_source_dialog_title),
            subtitle = stringResource(R.string.tracking_library_source_dialog_subtitle),
            options = trackingState.availableLibrarySourceModes.map { mode ->
                SettingsPickerOption(mode, librarySourceLabel(mode))
            },
            selectedValue = trackingState.librarySourceMode,
            onOptionSelected = { mode ->
                trackingViewModel.selectLibrarySourceMode(mode)
                showLibrarySourceDialog = false
            },
            onDismiss = { showLibrarySourceDialog = false },
            width = 620.dp,
            maxHeight = 340.dp
        )
    }

    if (showWatchProgressDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.tracking_watch_progress_dialog_title),
            subtitle = stringResource(R.string.tracking_watch_progress_dialog_subtitle),
            options = trackingState.availableWatchProgressSources.map { source ->
                SettingsPickerOption(source, watchProgressSourceLabel(source))
            },
            selectedValue = trackingState.watchProgressSource,
            onOptionSelected = { source ->
                trackingViewModel.selectWatchProgressSource(source)
                showWatchProgressDialog = false
            },
            onDismiss = { showWatchProgressDialog = false },
            width = 660.dp,
            maxHeight = 360.dp
        )
    }

    if (showDaysCapDialog) {
        val options = remember {
            listOf(
                14,
                30,
                60,
                90,
                180,
                365,
                TrackingSettingsDataStore.CONTINUE_WATCHING_DAYS_CAP_ALL
            )
        }
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.tracking_cw_window_title),
            subtitle = stringResource(R.string.tracking_cw_window_subtitle),
            options = options.map { days ->
                SettingsPickerOption(days, continueWatchingWindowLabel(days))
            },
            selectedValue = trackingState.continueWatchingDaysCap,
            onOptionSelected = { days ->
                trackingViewModel.selectContinueWatchingDaysCap(days)
                showDaysCapDialog = false
            },
            onDismiss = { showDaysCapDialog = false },
            width = 620.dp,
            maxHeight = 420.dp
        )
    }

    if (showMoreLikeThisSourceDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.tracking_more_like_this_source_dialog_title),
            subtitle = stringResource(R.string.tracking_more_like_this_source_dialog_subtitle),
            options = listOf(
                SettingsPickerOption(
                    MoreLikeThisSourcePreference.TMDB,
                    stringResource(R.string.tracking_more_like_this_source_tmdb)
                ),
                SettingsPickerOption(
                    MoreLikeThisSourcePreference.SIMKL,
                    stringResource(R.string.tracking_more_like_this_source_simkl)
                )
            ),
            selectedValue = trackingState.moreLikeThisSource,
            onOptionSelected = { source ->
                trackingViewModel.selectMoreLikeThisSource(source)
                showMoreLikeThisSourceDialog = false
            },
            onDismiss = { showMoreLikeThisSourceDialog = false },
            width = 620.dp,
            maxHeight = 320.dp
        )
    }

    if (showMdbListLibraryListsDialog) {
        MdbListLibraryListsDialog(
            state = mdbListLibraryLists,
            onToggle = mdbListViewModel::onToggleLibraryList,
            onDismiss = { showMdbListLibraryListsDialog = false }
        )
    }

    if (showAnimeIdDialog) {
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.tracking_simkl_anime_id_title),
            subtitle = stringResource(R.string.tracking_simkl_anime_id_subtitle),
            options = listOf(
                SettingsPickerOption(
                    SimklAnimeIdPreference.IMDB,
                    stringResource(R.string.tracking_simkl_anime_id_imdb)
                ),
                SettingsPickerOption(
                    SimklAnimeIdPreference.MAL,
                    stringResource(R.string.tracking_simkl_anime_id_mal)
                ),
                SettingsPickerOption(
                    SimklAnimeIdPreference.KITSU,
                    stringResource(R.string.tracking_simkl_anime_id_kitsu)
                ),
                SettingsPickerOption(
                    SimklAnimeIdPreference.TVDB,
                    stringResource(R.string.tracking_simkl_anime_id_tvdb)
                )
            ),
            selectedValue = trackingState.simklAnimeIdPreference,
            onOptionSelected = { preference ->
                trackingViewModel.selectSimklAnimeIdPreference(preference)
                showAnimeIdDialog = false
            },
            onDismiss = { showAnimeIdDialog = false },
            width = 620.dp,
            maxHeight = 360.dp
        )
    }
}

@Composable
internal fun TrackingSettingsOverview(
    simklState: SimklSettingsUiState,
    mdbListState: MdbListTrackerUiState,
    trackingState: TrackingSettingsUiState,
    simklFocusRequester: FocusRequester,
    mdbListFocusRequester: FocusRequester,
    libraryFocusRequester: FocusRequester,
    watchProgressFocusRequester: FocusRequester,
    continueWatchingFocusRequester: FocusRequester,
    moreLikeThisFocusRequester: FocusRequester,
    onSimklClick: () -> Unit,
    onMdbListClick: () -> Unit,
    onLibrarySourceClick: () -> Unit,
    onWatchProgressClick: () -> Unit,
    onContinueWatchingWindowClick: () -> Unit,
    onMoreLikeThisClick: () -> Unit,
    onAnimeIdClick: () -> Unit,
    mdbListLibraryLists: MdbListLibraryListsUiState = MdbListLibraryListsUiState(),
    onMdbListLibraryListsClick: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val simklPresentation = simklConnectionPresentation(simklState)
    val mdbListPresentation = mdbListConnectionPresentation(mdbListState)

    SettingsStandaloneScaffold(
        title = stringResource(R.string.settings_tracking_title),
        subtitle = stringResource(R.string.settings_tracking_description),
        classicContainer = false
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsDetailHeader(
                title = stringResource(R.string.settings_tracking_title),
                subtitle = stringResource(R.string.settings_tracking_description)
            )
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TrackingSettingsTestTags.OVERVIEW_LIST),
                    contentPadding = PaddingValues(bottom = NuvioTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "tracking_accounts") {
                        SettingsGroupCard(
                            title = stringResource(R.string.tracking_accounts_title),
                            subtitle = stringResource(R.string.tracking_accounts_subtitle)
                        ) {
                            SettingsActionRow(
                                title = stringResource(R.string.simkl_name),
                                subtitle = simklPresentation.subtitle,
                                value = simklPresentation.value,
                                valueColor = simklPresentation.color,
                                leadingRawIconRes = R.raw.simkl_tv_glyph,
                                leadingArtworkSize = 40.dp,
                                onClick = onSimklClick,
                                modifier = Modifier
                                    .focusRequester(simklFocusRequester)
                                    .testTag(TrackingSettingsTestTags.SIMKL_PROVIDER)
                            )
                            SettingsActionRow(
                                title = stringResource(R.string.mdblist_name),
                                subtitle = mdbListPresentation.subtitle,
                                value = mdbListPresentation.value,
                                valueColor = mdbListPresentation.color,
                                leadingRawIconRes = R.raw.mdblist_logo,
                                leadingArtworkSize = 40.dp,
                                onClick = onMdbListClick,
                                modifier = Modifier
                                    .focusRequester(mdbListFocusRequester)
                                    .testTag(TrackingSettingsTestTags.MDBLIST_PROVIDER)
                            )
                        }
                    }
                    item(key = "tracking_sources") {
                        SettingsGroupCard(
                            title = stringResource(R.string.tracking_sources_title),
                            subtitle = stringResource(R.string.tracking_sources_subtitle)
                        ) {
                            SettingsActionRow(
                                title = stringResource(R.string.tracking_library_source_title),
                                subtitle = stringResource(R.string.tracking_library_source_subtitle),
                                value = librarySourceLabel(trackingState.librarySourceMode),
                                enabled = trackingState.isReady,
                                onClick = onLibrarySourceClick,
                                modifier = Modifier
                                    .focusRequester(libraryFocusRequester)
                                    .testTag(TrackingSettingsTestTags.LIBRARY_SOURCE)
                            )
                            SettingsActionRow(
                                title = stringResource(R.string.tracking_watch_progress_title),
                                subtitle = stringResource(R.string.tracking_watch_progress_subtitle),
                                value = watchProgressSourceLabel(trackingState.watchProgressSource),
                                enabled = trackingState.isReady,
                                onClick = onWatchProgressClick,
                                modifier = Modifier
                                    .focusRequester(watchProgressFocusRequester)
                                    .testTag(TrackingSettingsTestTags.WATCH_PROGRESS_SOURCE)
                            )
                            if (simklState.mode == SimklConnectionMode.CONNECTED) {
                                SettingsActionRow(
                                    title = stringResource(R.string.tracking_more_like_this_source_title),
                                    subtitle = stringResource(R.string.tracking_more_like_this_source_subtitle),
                                    value = moreLikeThisSourceLabel(trackingState.moreLikeThisSource),
                                    enabled = true,
                                    onClick = onMoreLikeThisClick,
                                    modifier = Modifier
                                        .focusRequester(moreLikeThisFocusRequester)
                                        .testTag(TrackingSettingsTestTags.MORE_LIKE_THIS)
                                )
                            }
                            SettingsActionRow(
                                title = stringResource(R.string.tracking_continue_watching_window),
                                subtitle = stringResource(R.string.tracking_continue_watching_subtitle),
                                value = continueWatchingWindowLabel(trackingState.continueWatchingDaysCap),
                                enabled = trackingState.isReady,
                                onClick = onContinueWatchingWindowClick,
                                modifier = Modifier
                                    .focusRequester(continueWatchingFocusRequester)
                                    .testTag(TrackingSettingsTestTags.CONTINUE_WATCHING)
                            )
                        }
                    }
                    if (simklState.mode == SimklConnectionMode.CONNECTED) {
                        item(key = "tracking_simkl_features") {
                            SettingsGroupCard(
                                title = stringResource(R.string.tracking_simkl_features_title),
                                subtitle = stringResource(R.string.tracking_simkl_features_subtitle)
                            ) {
                                SettingsActionRow(
                                    title = stringResource(R.string.tracking_simkl_anime_id_title),
                                    subtitle = stringResource(R.string.tracking_simkl_anime_id_subtitle),
                                    value = animeIdPreferenceLabel(trackingState.simklAnimeIdPreference),
                                    onClick = onAnimeIdClick,
                                    modifier = Modifier.testTag("tracking_simkl_anime_id")
                                )
                            }
                        }
                    }
                    if (mdbListState.isConnected) {
                        item(key = "tracking_mdblist_features") {
                            SettingsGroupCard(
                                title = stringResource(R.string.tracking_mdblist_features_title),
                                subtitle = stringResource(R.string.tracking_mdblist_features_subtitle)
                            ) {
                                SettingsActionRow(
                                    title = stringResource(R.string.mdblist_library_lists),
                                    subtitle = stringResource(R.string.mdblist_library_lists_description),
                                    value = mdbListLibraryLists.lists.takeIf { it.isNotEmpty() }?.let { lists ->
                                        stringResource(R.string.mdblist_library_lists_summary, lists.count { it.visible }, lists.size)
                                    },
                                    onClick = onMdbListLibraryListsClick,
                                    modifier = Modifier.testTag(TrackingSettingsTestTags.MDBLIST_LIBRARY_LISTS)
                                )
                            }
                        }
                    }
                }
                SettingsVerticalScrollIndicators(state = listState)
            }
        }
    }
}

private data class TrackingConnectionPresentation(
    val subtitle: String,
    val value: String,
    val color: Color
)

@Composable
private fun simklConnectionPresentation(state: SimklSettingsUiState): TrackingConnectionPresentation {
    return when {
        state.isLoading && state.mode != SimklConnectionMode.CONNECTED -> TrackingConnectionPresentation(
            subtitle = stringResource(R.string.tracking_connecting_provider, stringResource(R.string.simkl_name)),
            value = stringResource(R.string.tracking_status_connecting),
            color = NuvioTheme.colors.Info
        )
        state.mode == SimklConnectionMode.CONNECTED -> TrackingConnectionPresentation(
            subtitle = stringResource(
                R.string.simkl_connected_as,
                state.username ?: stringResource(R.string.simkl_user_fallback)
            ),
            value = stringResource(R.string.tracking_status_connected),
            color = NuvioTheme.colors.Success
        )
        state.mode == SimklConnectionMode.AWAITING_APPROVAL -> TrackingConnectionPresentation(
            subtitle = stringResource(R.string.tracking_finish_connection),
            value = stringResource(R.string.tracking_status_waiting),
            color = NuvioTheme.colors.Warning
        )
        else -> TrackingConnectionPresentation(
            subtitle = stringResource(R.string.simkl_description),
            value = stringResource(R.string.tracking_status_disconnected),
            color = NuvioTheme.colors.TextSecondary
        )
    }
}

@Composable
private fun mdbListConnectionPresentation(state: MdbListTrackerUiState): TrackingConnectionPresentation = when {
    state.isLoading && !state.isConnected -> TrackingConnectionPresentation(
        stringResource(R.string.tracking_connecting_provider, stringResource(R.string.mdblist_name)),
        stringResource(R.string.tracking_status_connecting), NuvioTheme.colors.Info
    )
    state.isConnected -> TrackingConnectionPresentation(
        stringResource(R.string.mdblist_connected_as, state.username ?: stringResource(R.string.mdblist_account_fallback)),
        stringResource(R.string.tracking_status_connected), NuvioTheme.colors.Success
    )
    state.session != null -> TrackingConnectionPresentation(
        state.errorMessage ?: stringResource(R.string.tracking_finish_connection),
        stringResource(R.string.tracking_status_waiting), NuvioTheme.colors.Warning
    )
    else -> TrackingConnectionPresentation(
        state.errorMessage ?: stringResource(R.string.mdblist_tracking_description),
        stringResource(R.string.tracking_status_disconnected), NuvioTheme.colors.TextSecondary
    )
}

@Composable
private fun watchProgressSourceLabel(source: WatchProgressSource): String = when (source) {
    WatchProgressSource.SIMKL -> stringResource(R.string.simkl_name)
    WatchProgressSource.MDBLIST -> stringResource(R.string.mdblist_name)
    WatchProgressSource.NUVIO_SYNC -> stringResource(R.string.tracking_watch_progress_source_nuvio)
}

@Composable
private fun librarySourceLabel(mode: LibrarySourceMode): String = when (mode) {
    LibrarySourceMode.SIMKL -> stringResource(R.string.simkl_name)
    LibrarySourceMode.MDBLIST -> stringResource(R.string.mdblist_name)
    LibrarySourceMode.LOCAL -> stringResource(R.string.tracking_library_source_nuvio)
}

@Composable
private fun moreLikeThisSourceLabel(source: MoreLikeThisSourcePreference): String = when (source) {
    MoreLikeThisSourcePreference.TMDB -> stringResource(R.string.tracking_more_like_this_source_tmdb)
    MoreLikeThisSourcePreference.SIMKL -> stringResource(R.string.tracking_more_like_this_source_simkl)
}

@Composable
private fun continueWatchingWindowLabel(days: Int): String {
    return if (days == TrackingSettingsDataStore.CONTINUE_WATCHING_DAYS_CAP_ALL) {
        stringResource(R.string.tracking_all_history)
    } else {
        stringResource(R.string.tracking_days_format, days)
    }
}

@Composable
private fun animeIdPreferenceLabel(preference: SimklAnimeIdPreference): String = when (preference) {
    SimklAnimeIdPreference.IMDB -> stringResource(R.string.tracking_simkl_anime_id_imdb)
    SimklAnimeIdPreference.MAL -> stringResource(R.string.tracking_simkl_anime_id_mal)
    SimklAnimeIdPreference.KITSU -> stringResource(R.string.tracking_simkl_anime_id_kitsu)
    SimklAnimeIdPreference.TVDB -> stringResource(R.string.tracking_simkl_anime_id_tvdb)
}

private enum class TrackingFocusTarget {
    SIMKL,
    MDBLIST,
    LIBRARY,
    WATCH_PROGRESS,
    CONTINUE_WATCHING,
    MORE_LIKE_THIS
}

internal object TrackingSettingsTestTags {
    const val OVERVIEW_LIST = "tracking_overview_list"
    const val SIMKL_PROVIDER = "tracking_provider_simkl"
    const val MDBLIST_PROVIDER = "tracking_provider_mdblist"
    const val MDBLIST_LIBRARY_LISTS = "tracking_mdblist_library_lists"
    const val LIBRARY_SOURCE = "tracking_source_library"
    const val WATCH_PROGRESS_SOURCE = "tracking_source_watch_progress"
    const val CONTINUE_WATCHING = "tracking_continue_watching_window"
    const val MORE_LIKE_THIS = "tracking_more_like_this_source"
}
