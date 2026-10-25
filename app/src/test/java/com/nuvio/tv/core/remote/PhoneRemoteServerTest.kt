package com.nuvio.tv.core.remote

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PhoneRemoteServerTest {
    @Test
    fun serverRefusesBindAddressesOutsidePrivateLanOrLoopback() {
        assertTrue(runCatching { newServer("0.0.0.0") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { newServer("8.8.8.8") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { newServer("192.168.1.20") }.isSuccess)
    }

    @Test
    fun startRemote_resolvesAnEphemeralPortWithinTheStartupBudget() {
        val server = newServer()
        try {
            val baseUrl = runBlocking {
                withTimeout(15_000L) { server.startRemote() }
            }

            assertTrue(baseUrl.matches(Regex("http://127\\.0\\.0\\.1:[1-9][0-9]{0,4}")))
        } finally {
            server.stopRemote()
        }
    }

    @Test
    fun pageIsServedOnTheBoundInterfaceWithNoCacheAndControlsRequirePairing() {
        val server = newServer()
        val client = OkHttpClient()
        try {
            val baseUrl = runBlocking { server.startRemote() }
            server.openPairingWindow()

            val response = client.newCall(Request.Builder().url(baseUrl).build()).execute()
            response.use {
                assertEquals(200, it.code)
                assertEquals("no-store", it.header("Cache-Control"))
                val html = it.body?.string().orEmpty()
                assertTrue(html.contains("Digite o código exibido na TV"))
                assertTrue(html.contains("data-action=\"select\""))
                assertTrue(html.contains("id=\"text\""))
            }
        } finally {
            server.stopRemote()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun exactOriginPairingRequiresTvApprovalBeforeDispatchingCommands() {
        val pending = AtomicReference<PhoneRemotePairingAuthority.PendingPhonePairing>()
        val pendingReady = CountDownLatch(1)
        val command = AtomicReference<PhoneRemoteCommand>()
        val commandReady = CountDownLatch(1)
        val server = PhoneRemoteServer(
            bindAddress = LOOPBACK,
            onPairingRequested = {
                pending.set(it)
                pendingReady.countDown()
            },
            onCommand = {
                command.set(it)
                commandReady.countDown()
            }
        )
        val client = OkHttpClient()
        val listener = RecordingListener()
        var socket: WebSocket? = null
        try {
            val baseUrl = runBlocking { server.startRemote() }
            val challenge = server.openPairingWindow()
            socket = client.newWebSocket(webSocketRequest(baseUrl, baseUrl), listener)
            assertTrue(listener.opened.await(3, TimeUnit.SECONDS))

            assertTrue(socket.send("""{"type":"pair","code":"${challenge.displayCode}","deviceName":"Phone test"}"""))
            assertTrue(pendingReady.await(3, TimeUnit.SECONDS))
            assertFalse(commandReady.await(100, TimeUnit.MILLISECONDS))

            val pairing = pending.get()
            assertNotNull(pairing)
            val pendingFrame = JsonParser.parseString(listener.messages.poll(3, TimeUnit.SECONDS)).asJsonObject
            assertEquals("pairing_pending", pendingFrame.get("type").asString)
            val approval = server.approvePairing(pairing.requestId)
            assertTrue(approval is PhoneRemotePairingAuthority.PairingApprovalResult.Approved)
            val paired = JsonParser.parseString(listener.messages.poll(3, TimeUnit.SECONDS)).asJsonObject
            assertEquals("paired", paired.get("type").asString)
            assertEquals(64, paired.get("token").asString.length)

            assertTrue(socket.send("""{"type":"command","action":"select"}"""))
            assertTrue(commandReady.await(3, TimeUnit.SECONDS))
            assertEquals(
                PhoneRemoteCommand.Action(PhoneRemoteCommand.ActionType.SELECT),
                command.get()
            )
        } finally {
            socket?.close(1000, "test complete")
            server.stopRemote()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun rejectedPairingReturnsToPairingStateWithoutDispatchingCommands() {
        val pending = AtomicReference<PhoneRemotePairingAuthority.PendingPhonePairing>()
        val pendingReady = CountDownLatch(1)
        val commands = java.util.concurrent.CopyOnWriteArrayList<PhoneRemoteCommand>()
        val server = PhoneRemoteServer(
            bindAddress = LOOPBACK,
            onPairingRequested = {
                pending.set(it)
                pendingReady.countDown()
            },
            onCommand = { commands.add(it) }
        )
        val client = OkHttpClient()
        val listener = RecordingListener()
        var socket: WebSocket? = null
        try {
            val baseUrl = runBlocking { server.startRemote() }
            val challenge = server.openPairingWindow()
            socket = client.newWebSocket(webSocketRequest(baseUrl, baseUrl), listener)
            assertTrue(listener.opened.await(3, TimeUnit.SECONDS))
            assertTrue(socket.send("""{"type":"pair","code":"${challenge.displayCode}"}"""))
            assertTrue(pendingReady.await(3, TimeUnit.SECONDS))
            assertEquals(
                "pairing_pending",
                JsonParser.parseString(listener.messages.poll(3, TimeUnit.SECONDS)).asJsonObject.get("type").asString
            )

            assertTrue(server.rejectPairing(pending.get().requestId))
            assertEquals(
                "rejected",
                JsonParser.parseString(listener.messages.poll(3, TimeUnit.SECONDS)).asJsonObject.get("type").asString
            )
            assertTrue(socket.send("""{"type":"command","action":"home"}"""))
            val error = JsonParser.parseString(listener.messages.poll(3, TimeUnit.SECONDS)).asJsonObject
            assertEquals("error", error.get("type").asString)
            assertEquals("Pairing required", error.get("message").asString)
            assertTrue(commands.isEmpty())
        } finally {
            socket?.close(1000, "test complete")
            server.stopRemote()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun pairingPendingFrameIsQueuedBeforeSynchronousApprovalCallback() {
        val serverReference = AtomicReference<PhoneRemoteServer>()
        val approvalReady = CountDownLatch(1)
        val server = PhoneRemoteServer(
            bindAddress = LOOPBACK,
            onPairingRequested = { request ->
                serverReference.get().approvePairing(request.requestId)
                approvalReady.countDown()
            },
            onCommand = {}
        )
        serverReference.set(server)
        val client = OkHttpClient()
        val listener = RecordingListener()
        var socket: WebSocket? = null
        try {
            val baseUrl = runBlocking { server.startRemote() }
            val challenge = server.openPairingWindow()
            socket = client.newWebSocket(webSocketRequest(baseUrl, baseUrl), listener)
            assertTrue(listener.opened.await(3, TimeUnit.SECONDS))
            assertTrue(socket.send("""{"type":"pair","code":"${challenge.displayCode}"}"""))
            assertTrue(approvalReady.await(3, TimeUnit.SECONDS))

            val pendingFrame = JsonParser.parseString(listener.messages.poll(3, TimeUnit.SECONDS)).asJsonObject
            assertEquals("pairing_pending", pendingFrame.get("type").asString)
            val pairedFrame = JsonParser.parseString(listener.messages.poll(3, TimeUnit.SECONDS)).asJsonObject
            assertEquals("paired", pairedFrame.get("type").asString)
        } finally {
            socket?.close(1000, "test complete")
            server.stopRemote()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun rejectsWrongOriginAndDoesNotDispatchUnauthenticatedCommands() {
        val commands = java.util.concurrent.CopyOnWriteArrayList<PhoneRemoteCommand>()
        val server = PhoneRemoteServer(
            bindAddress = LOOPBACK,
            onPairingRequested = {},
            onCommand = { commands.add(it) }
        )
        val client = OkHttpClient()
        try {
            val baseUrl = runBlocking { server.startRemote() }
            val badOriginListener = RecordingListener()
            client.newWebSocket(
                webSocketRequest(baseUrl, "http://attacker.example"),
                badOriginListener
            )
            assertTrue(badOriginListener.failed.await(3, TimeUnit.SECONDS))
            assertEquals(403, badOriginListener.failureResponse.get()?.code)
            assertEquals(1L, badOriginListener.opened.count)

            val unauthenticated = RecordingListener()
            val socket = client.newWebSocket(webSocketRequest(baseUrl, baseUrl), unauthenticated)
            assertTrue(unauthenticated.opened.await(3, TimeUnit.SECONDS))
            assertTrue(socket.send("""{"type":"command","action":"home"}"""))
            val errorMessage = unauthenticated.messages.poll(3, TimeUnit.SECONDS)
            assertNotNull(errorMessage)
            val error = JsonParser.parseString(errorMessage).asJsonObject
            assertEquals("error", error.get("type").asString)
            assertEquals("Pairing required", error.get("message").asString)
            assertTrue(commands.isEmpty())
            socket.close(1000, "test complete")
        } finally {
            server.stopRemote()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    private fun newServer(bindAddress: String = LOOPBACK): PhoneRemoteServer = PhoneRemoteServer(
        bindAddress = bindAddress,
        onPairingRequested = {},
        onCommand = {}
    )

    private fun webSocketRequest(baseUrl: String, origin: String): Request =
        Request.Builder()
            .url(baseUrl.replaceFirst("http://", "ws://") + "/ws")
            .header("Origin", origin)
            .build()

    private class RecordingListener : WebSocketListener() {
        val opened = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val messages = LinkedBlockingQueue<String>()
        val failureResponse = AtomicReference<Response?>()
        val closeCode = AtomicReference<Int>()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.countDown()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            messages.offer(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closeCode.set(code)
            closed.countDown()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            failureResponse.set(response)
            failed.countDown()
            closed.countDown()
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
