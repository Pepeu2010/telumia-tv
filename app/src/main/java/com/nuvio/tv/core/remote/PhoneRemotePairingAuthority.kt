package com.nuvio.tv.core.remote

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * In-memory authority for the explicit, short-lived pairing flow used by Phone Remote.
 * It deliberately does not persist a pairing code or session token across server restarts.
 */
internal class PhoneRemotePairingAuthority(
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val random: SecureRandom = SecureRandom()
) {
    private data class ChallengeState(
        val code: String,
        val expiresAtElapsedMs: Long,
        var failedAttempts: Int = 0
    )

    private data class SessionState(
        val tokenDigest: ByteArray,
        val createdAtElapsedMs: Long,
        var lastActivityElapsedMs: Long
    )

    private var challenge: ChallengeState? = null
    private var pendingRequest: PendingPhonePairing? = null
    private var session: SessionState? = null

    @Synchronized
    fun openPairingWindow(): PairingChallenge {
        val now = elapsedRealtimeMs()
        val code = String.format(Locale.ROOT, "%06d", random.nextInt(PAIRING_CODE_SPACE))
        challenge = ChallengeState(code, now + PAIRING_WINDOW_MS)
        pendingRequest = null
        session = null
        return PairingChallenge(code, now + PAIRING_WINDOW_MS)
    }

    @Synchronized
    fun requestPairing(
        code: String,
        deviceName: String?,
        peerAddress: String?
    ): PairingRequestResult {
        val now = elapsedRealtimeMs()
        val activeChallenge = challenge ?: return PairingRequestResult.Expired
        if (now >= activeChallenge.expiresAtElapsedMs) {
            clearPairingWindow()
            return PairingRequestResult.Expired
        }
        if (pendingRequest != null) return PairingRequestResult.RequestAlreadyPending

        if (!constantTimeEquals(activeChallenge.code, code)) {
            activeChallenge.failedAttempts += 1
            val attemptsLeft = MAX_FAILED_ATTEMPTS - activeChallenge.failedAttempts
            if (attemptsLeft <= 0) {
                clearPairingWindow()
                return PairingRequestResult.TooManyAttempts
            }
            return PairingRequestResult.InvalidCode(attemptsLeft)
        }

        val request = PendingPhonePairing(
            requestId = randomHex(REQUEST_ID_BYTES),
            deviceName = sanitizeLabel(deviceName, fallback = "Celular"),
            peerAddress = sanitizeLabel(peerAddress, fallback = "Dispositivo local"),
            requestedAtElapsedMs = now,
            expiresAtElapsedMs = activeChallenge.expiresAtElapsedMs
        )
        pendingRequest = request
        return PairingRequestResult.WaitingForApproval(request)
    }

    @Synchronized
    fun approve(requestId: String): PairingApprovalResult {
        val now = elapsedRealtimeMs()
        val activeChallenge = challenge ?: return PairingApprovalResult.NoPendingRequest
        val request = pendingRequest ?: return PairingApprovalResult.NoPendingRequest
        if (now >= activeChallenge.expiresAtElapsedMs) {
            clearPairingWindow()
            return PairingApprovalResult.Expired
        }
        if (!constantTimeEquals(request.requestId, requestId)) {
            return PairingApprovalResult.NoPendingRequest
        }

        val token = randomHex(SESSION_TOKEN_BYTES)
        session = SessionState(
            tokenDigest = sha256(token),
            createdAtElapsedMs = now,
            lastActivityElapsedMs = now
        )
        clearPairingWindow()
        return PairingApprovalResult.Approved(
            sessionToken = token,
            expiresAtElapsedMs = now + SESSION_MAX_AGE_MS
        )
    }

    @Synchronized
    fun reject(requestId: String): Boolean {
        val request = pendingRequest ?: return false
        if (!constantTimeEquals(request.requestId, requestId)) return false
        pendingRequest = null
        return true
    }

    @Synchronized
    fun isAuthorized(sessionToken: String): Boolean {
        val activeSession = session ?: return false
        val now = elapsedRealtimeMs()
        val expiredByAge = now - activeSession.createdAtElapsedMs >= SESSION_MAX_AGE_MS
        val expiredByInactivity = now - activeSession.lastActivityElapsedMs >= SESSION_IDLE_TIMEOUT_MS
        if (expiredByAge || expiredByInactivity) {
            session = null
            return false
        }
        val suppliedDigest = sha256(sessionToken)
        if (!MessageDigest.isEqual(activeSession.tokenDigest, suppliedDigest)) return false
        activeSession.lastActivityElapsedMs = now
        return true
    }

    @Synchronized
    fun revoke(): Boolean {
        val hadState = challenge != null || pendingRequest != null || session != null
        challenge = null
        pendingRequest = null
        session = null
        return hadState
    }

    @Synchronized
    fun currentPendingRequest(): PendingPhonePairing? {
        val request = pendingRequest ?: return null
        if (elapsedRealtimeMs() >= request.expiresAtElapsedMs) {
            clearPairingWindow()
            return null
        }
        return request
    }

    @Synchronized
    fun pairingWindowIsOpen(): Boolean {
        val activeChallenge = challenge ?: return false
        if (elapsedRealtimeMs() >= activeChallenge.expiresAtElapsedMs) {
            clearPairingWindow()
            return false
        }
        return true
    }

    private fun clearPairingWindow() {
        challenge = null
        pendingRequest = null
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return buildString(byteCount * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.US_ASCII))

    private fun constantTimeEquals(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            actual.toByteArray(StandardCharsets.US_ASCII)
        )

    private fun sanitizeLabel(value: String?, fallback: String): String = value
        .orEmpty()
        .filterNot(Char::isISOControl)
        .trim()
        .take(MAX_LABEL_LENGTH)
        .ifEmpty { fallback }

    data class PairingChallenge(
        val displayCode: String,
        val expiresAtElapsedMs: Long
    )

    data class PendingPhonePairing(
        val requestId: String,
        val deviceName: String,
        val peerAddress: String,
        val requestedAtElapsedMs: Long,
        val expiresAtElapsedMs: Long
    )

    sealed interface PairingRequestResult {
        data class WaitingForApproval(val request: PendingPhonePairing) : PairingRequestResult
        data class InvalidCode(val attemptsRemaining: Int) : PairingRequestResult
        data object TooManyAttempts : PairingRequestResult
        data object RequestAlreadyPending : PairingRequestResult
        data object Expired : PairingRequestResult
    }

    sealed interface PairingApprovalResult {
        data class Approved(val sessionToken: String, val expiresAtElapsedMs: Long) : PairingApprovalResult
        data object NoPendingRequest : PairingApprovalResult
        data object Expired : PairingApprovalResult
    }

    private companion object {
        const val PAIRING_CODE_SPACE = 1_000_000
        const val PAIRING_WINDOW_MS = 2 * 60 * 1000L
        const val MAX_FAILED_ATTEMPTS = 5
        const val SESSION_IDLE_TIMEOUT_MS = 15 * 60 * 1000L
        const val SESSION_MAX_AGE_MS = 8 * 60 * 60 * 1000L
        const val REQUEST_ID_BYTES = 16
        const val SESSION_TOKEN_BYTES = 32
        const val MAX_LABEL_LENGTH = 48
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val HEX = "0123456789abcdef"
    }
}
