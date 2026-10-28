package com.nuvio.tv.core.remote

import android.graphics.Bitmap
import com.nuvio.tv.core.qr.QrCodeGenerator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class PhoneRemoteStatus {
    Off,
    Starting,
    Active,
    PairingExpired,
    PairingPending,
    Paired,
    NetworkUnavailable,
    ServerError
}

internal data class PhoneRemoteUiState(
    val status: PhoneRemoteStatus = PhoneRemoteStatus.Off,
    val endpoint: String? = null,
    val pairingCode: String? = null,
    val pairingExpiresAtElapsedMs: Long? = null,
    val qrCode: Bitmap? = null,
    val pendingPairing: PhoneRemotePairingAuthority.PendingPhonePairing? = null
) {
    val isEnabled: Boolean get() = endpoint != null
    val isStarting: Boolean get() = status == PhoneRemoteStatus.Starting
}

/** Process-scoped opt-in owner so the LAN listener remains active while the user leaves Settings. */
@Singleton
internal class PhoneRemoteController @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(PhoneRemoteUiState())
    val state = mutableState.asStateFlow()

    @Volatile
    private var server: PhoneRemoteServer? = null
    private var pairingExpiryJob: Job? = null

    fun enable() {
        if (server != null || mutableState.value.isStarting) return
        mutableState.value = PhoneRemoteUiState(status = PhoneRemoteStatus.Starting)
        scope.launch {
            val address = PhoneRemoteLanAddress.resolve()
            if (address == null) {
                mutableState.value = PhoneRemoteUiState(status = PhoneRemoteStatus.NetworkUnavailable)
                return@launch
            }

            val candidate = PhoneRemoteServer(
                bindAddress = address,
                onPairingRequested = { request ->
                    mutableState.update {
                        it.copy(status = PhoneRemoteStatus.PairingPending, pendingPairing = request)
                    }
                },
                onCommand = PhoneRemoteCommandBus::dispatch
            )
            try {
                val endpoint = candidate.startRemote()
                server = candidate
                mutableState.value = PhoneRemoteUiState(
                    status = PhoneRemoteStatus.Active,
                    endpoint = endpoint
                )
                createPairingWindow(candidate)
            } catch (_: Throwable) {
                candidate.close()
                server = null
                mutableState.value = PhoneRemoteUiState(status = PhoneRemoteStatus.ServerError)
            }
        }
    }

    fun createNewPairingCode() {
        val activeServer = server ?: return
        scope.launch { createPairingWindow(activeServer) }
    }

    fun approvePairing(requestId: String) {
        val activeServer = server ?: return
        when (activeServer.approvePairing(requestId)) {
            is PhoneRemotePairingAuthority.PairingApprovalResult.Approved -> mutableState.update {
                it.copy(status = PhoneRemoteStatus.Paired, pendingPairing = null)
            }
            PhoneRemotePairingAuthority.PairingApprovalResult.Expired -> mutableState.update {
                it.copy(status = PhoneRemoteStatus.PairingExpired, pendingPairing = null)
            }
            PhoneRemotePairingAuthority.PairingApprovalResult.NoPendingRequest -> mutableState.update {
                it.copy(status = PhoneRemoteStatus.Active, pendingPairing = null)
            }
        }
    }

    fun rejectPairing(requestId: String) {
        val rejected = server?.rejectPairing(requestId) == true
        if (rejected) {
            mutableState.update { it.copy(status = PhoneRemoteStatus.Active, pendingPairing = null) }
        }
    }

    fun disable() {
        scope.launch { disableOnIo() }
    }

    /** Synchronous process teardown used only when the owning TV activity is finishing. */
    fun shutdown() {
        pairingExpiryJob?.cancel()
        pairingExpiryJob = null
        server?.close()
        server = null
        scope.cancel()
        mutableState.value = PhoneRemoteUiState()
    }

    private suspend fun createPairingWindow(activeServer: PhoneRemoteServer) {
        val challenge = activeServer.openPairingWindow()
        val endpoint = mutableState.value.endpoint ?: return
        val qrCode = runCatching {
            withContext(Dispatchers.Default) {
                QrCodeGenerator.generate(
                    content = "$endpoint?code=${challenge.displayCode}",
                    size = 360,
                    margin = 1
                )
            }
        }.getOrNull()
        pairingExpiryJob?.cancel()
        mutableState.update {
            it.copy(
                status = PhoneRemoteStatus.Active,
                pairingCode = challenge.displayCode,
                pairingExpiresAtElapsedMs = challenge.expiresAtElapsedMs,
                qrCode = qrCode,
                pendingPairing = null
            )
        }
        pairingExpiryJob = scope.launch {
            val remaining = (challenge.expiresAtElapsedMs - elapsedRealtimeMs()).coerceAtLeast(0L)
            delay(remaining)
            mutableState.update { current ->
                if (current.pairingExpiresAtElapsedMs == challenge.expiresAtElapsedMs) {
                    current.copy(
                        status = if (current.status == PhoneRemoteStatus.Paired) {
                            PhoneRemoteStatus.Paired
                        } else {
                            PhoneRemoteStatus.PairingExpired
                        },
                        pairingCode = null,
                        pairingExpiresAtElapsedMs = null,
                        qrCode = null,
                        pendingPairing = null
                    )
                } else current
            }
        }
    }

    private suspend fun disableOnIo() {
        pairingExpiryJob?.cancel()
        pairingExpiryJob = null
        val previousServer = server
        server = null
        previousServer?.stopRemote()
        mutableState.value = PhoneRemoteUiState()
    }

    private fun elapsedRealtimeMs(): Long = System.nanoTime() / 1_000_000L
}
