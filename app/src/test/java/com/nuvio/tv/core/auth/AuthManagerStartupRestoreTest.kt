package com.nuvio.tv.core.auth

import com.nuvio.tv.data.local.AuthSessionNoticeDataStore
import com.nuvio.tv.data.repository.AuthDiagnosticReportRepository
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.ServerCapabilities
import com.nuvio.tv.domain.model.ServerConfiguration
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.Postgrest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthManagerStartupRestoreTest {
    @Test
    fun `restores cached account while remote session validation is still pending`() = runBlocking {
        val statuses = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val auth = mockk<Auth>()
        val cachedUser = mockk<UserInfo>()
        val remoteValidationStarted = CompletableDeferred<Unit>()
        val remoteValidationResult = CompletableDeferred<UserInfo>()

        every { auth.sessionStatus } returns statuses
        every { auth.currentUserOrNull() } returns cachedUser
        every { auth.currentAccessTokenOrNull() } returns "cached-access-token"
        every { cachedUser.id } returns "cached-user-id"
        every { cachedUser.email } returns "cached@example.invalid"
        coEvery { auth.retrieveUserForCurrentSession(false) } coAnswers {
            remoteValidationStarted.complete(Unit)
            remoteValidationResult.await()
        }

        val noticeStore = mockk<AuthSessionNoticeDataStore>(relaxed = true)
        val diagnosticReports = mockk<AuthDiagnosticReportRepository>(relaxed = true)
        val manager = AuthManager(
            auth = auth,
            postgrest = mockk<Postgrest>(relaxed = true),
            httpClient = OkHttpClient(),
            customServerHttpClient = OkHttpClient(),
            authSessionNoticeDataStore = noticeStore,
            accountLocalDataResetService = mockk(relaxed = true),
            authDiagnosticReportRepository = diagnosticReports,
            serverConfiguration = ServerConfiguration(
                backendUrl = "https://nuvio.test",
                publishableKey = "test-key",
                capabilities = ServerCapabilities(emailPasswordAuth = false, tvLogin = true),
                isCustom = false
            )
        )

        try {
            statuses.value = mockk<SessionStatus.Authenticated>()

            withTimeout(2_000) { remoteValidationStarted.await() }

            assertEquals(
                AuthState.FullAccount("cached-user-id", "cached@example.invalid"),
                manager.authState.value
            )
            assertFalse("remote validation should still be pending", remoteValidationResult.isCompleted)
        } finally {
            remoteValidationResult.complete(cachedUser)
        }
    }
}
