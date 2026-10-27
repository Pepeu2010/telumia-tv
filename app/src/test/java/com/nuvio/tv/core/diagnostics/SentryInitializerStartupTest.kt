package com.nuvio.tv.core.diagnostics

import com.nuvio.tv.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SentryInitializerStartupTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `preference read is asynchronous and duplicate values are ignored`() = runTest {
        val storedValue = CompletableDeferred<Boolean>()
        val sourceStarted = CompletableDeferred<Unit>()
        val values = flow {
            sourceStarted.complete(Unit)
            emit(storedValue.await())
            emit(true)
            emit(true)
        }
        val applied = mutableListOf<Boolean>()

        val observer = observeSentryEnabled(
            scope = this,
            enabled = values,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            mainDispatcher = mainDispatcherRule.dispatcher,
            onEnabledChanged = applied::add,
        )

        assertFalse(sourceStarted.isCompleted)
        runCurrent()
        assertTrue(sourceStarted.isCompleted)
        assertTrue(applied.isEmpty())

        storedValue.complete(false)
        runCurrent()

        assertEquals(listOf(false, true), applied)
        observer.cancelAndJoin()
    }
}
