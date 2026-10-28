package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.nuvio.tv.core.remote.PhoneRemoteController
import com.nuvio.tv.core.remote.PhoneRemoteUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class PhoneRemoteSettingsViewModel @Inject constructor(
    private val controller: PhoneRemoteController
) : ViewModel() {
    val state: kotlinx.coroutines.flow.StateFlow<PhoneRemoteUiState> = controller.state

    fun enable() = controller.enable()
    fun createNewPairingCode() = controller.createNewPairingCode()
    fun approvePairing(requestId: String) = controller.approvePairing(requestId)
    fun rejectPairing(requestId: String) = controller.rejectPairing(requestId)
    fun disable() = controller.disable()
}
