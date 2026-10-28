package com.nuvio.tv.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRemotePairingAuthorityTest {
    private var nowMs = 10_000L
    private val authority = PhoneRemotePairingAuthority(elapsedRealtimeMs = { nowMs })

    @Test
    fun pairingRequiresAValidTemporaryCodeAndExplicitApproval() {
        val challenge = authority.openPairingWindow()
        assertTrue(challenge.displayCode.matches(Regex("\\d{6}")))

        val incorrectCode = if (challenge.displayCode == "000000") "000001" else "000000"
        val invalid = authority.requestPairing(incorrectCode, "Phone", "192.168.1.4")
        assertTrue(invalid is PhoneRemotePairingAuthority.PairingRequestResult.InvalidCode)
        assertFalse(authority.isAuthorized("anything"))

        val request = authority.requestPairing(
            challenge.displayCode,
            "  Living room phone  ",
            "192.168.1.4"
        ) as PhoneRemotePairingAuthority.PairingRequestResult.WaitingForApproval
        assertEquals("Living room phone", request.request.deviceName)
        assertEquals("192.168.1.4", request.request.peerAddress)
        assertFalse(authority.isAuthorized("anything"))

        val approval = authority.approve(request.request.requestId)
            as PhoneRemotePairingAuthority.PairingApprovalResult.Approved
        assertEquals(64, approval.sessionToken.length)
        assertTrue(authority.isAuthorized(approval.sessionToken))
        assertFalse(authority.isAuthorized("0".repeat(64)))
    }

    @Test
    fun aPairingWindowExpiresAtItsDeadline() {
        val challenge = authority.openPairingWindow()
        nowMs = challenge.expiresAtElapsedMs

        assertFalse(authority.pairingWindowIsOpen())
        assertEquals(
            PhoneRemotePairingAuthority.PairingRequestResult.Expired,
            authority.requestPairing(challenge.displayCode, null, null)
        )
    }

    @Test
    fun fiveInvalidCodesCloseThePairingWindow() {
        authority.openPairingWindow()

        repeat(4) {
            val result = authority.requestPairing("wrong", null, null)
            assertTrue(result is PhoneRemotePairingAuthority.PairingRequestResult.InvalidCode)
        }
        assertEquals(
            PhoneRemotePairingAuthority.PairingRequestResult.TooManyAttempts,
            authority.requestPairing("wrong", null, null)
        )
        assertFalse(authority.pairingWindowIsOpen())
    }

    @Test
    fun rejectedPairingDoesNotIssueASessionAndCanBeRetried() {
        val challenge = authority.openPairingWindow()
        val first = authority.requestPairing(challenge.displayCode, "Phone", null)
            as PhoneRemotePairingAuthority.PairingRequestResult.WaitingForApproval

        assertFalse(authority.isAuthorized(""))
        assertTrue(authority.reject(first.request.requestId))
        assertNull(authority.currentPendingRequest())

        val second = authority.requestPairing(challenge.displayCode, "Phone 2", null)
            as PhoneRemotePairingAuthority.PairingRequestResult.WaitingForApproval
        assertNotNull(authority.currentPendingRequest())
        assertFalse(authority.approve(first.request.requestId) is PhoneRemotePairingAuthority.PairingApprovalResult.Approved)
        assertTrue(authority.approve(second.request.requestId) is PhoneRemotePairingAuthority.PairingApprovalResult.Approved)
    }

    @Test
    fun activeSessionExpiresAfterInactivityOrRevocation() {
        val challenge = authority.openPairingWindow()
        val request = authority.requestPairing(challenge.displayCode, "Phone", null)
            as PhoneRemotePairingAuthority.PairingRequestResult.WaitingForApproval
        val approval = authority.approve(request.request.requestId)
            as PhoneRemotePairingAuthority.PairingApprovalResult.Approved

        nowMs += 14 * 60 * 1000L
        assertTrue(authority.isAuthorized(approval.sessionToken))
        nowMs += 15 * 60 * 1000L
        assertFalse(authority.isAuthorized(approval.sessionToken))

        val nextChallenge = authority.openPairingWindow()
        val nextRequest = authority.requestPairing(nextChallenge.displayCode, "Phone", null)
            as PhoneRemotePairingAuthority.PairingRequestResult.WaitingForApproval
        val nextApproval = authority.approve(nextRequest.request.requestId)
            as PhoneRemotePairingAuthority.PairingApprovalResult.Approved
        assertTrue(authority.revoke())
        assertFalse(authority.isAuthorized(nextApproval.sessionToken))
        assertFalse(authority.revoke())
    }
}
