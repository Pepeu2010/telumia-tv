package com.nuvio.tv.ui.screens.player

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.domain.model.NavigationMotion
import com.nuvio.tv.ui.components.TrailerPlayer
import com.nuvio.tv.ui.theme.NuvioTheme
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Local Media3 playback and HTTP transport cases. No public media URL or account is used. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class TrailerPlaybackMatrixTvTest {
    @get:Rule
    val compose = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private lateinit var fixture: File
    private lateinit var fixtureBytes: ByteArray
    private lateinit var server: LocalTrailerHttpServer

    @Before
    fun setUp() {
        fixture = originalTimelineVideo(context)
        fixtureBytes = fixture.readBytes()
        server = LocalTrailerHttpServer(fixtureBytes)
    }

    @After
    fun tearDown() {
        server.close()
        fixture.delete()
    }

    @Test
    fun progressiveMp4FromLoopbackRendersItsFirstFrame() {
        val mounted = mountTrailer(server.url("/trailer.mp4"))
        try {
            awaitOutcome(mounted)
            assertTrue("The local MP4 should render a video frame", mounted.firstFrame.get())
            assertNull("A valid local MP4 should not fail", mounted.failure.get())
            assertTrue("Media3 should request the local MP4", server.awaitRequest("/trailer.mp4"))
        } finally {
            unmount(mounted)
        }
    }

    @Test
    fun hlsFailureCreatesAReportWithoutSignedUrlData() {
        val signedUrl = server.url("/hls/master.m3u8?X-Amz-Signature=url-signature")
        val report = AtomicReference<PlaybackFailureReport?>()
        val mounted = mountTrailer(signedUrl) { error ->
            report.set(
                PlaybackFailureReport.create(
                    source = PlaybackFailureSource.TRAILER,
                    stage = PlaybackFailureStage.STARTUP,
                    mediaUrl = signedUrl,
                    firstFrameRendered = false,
                    errorCode = error.errorCodeName,
                    errorMessage = error.message
                )
            )
        }
        try {
            awaitOutcome(mounted)
            assertNotNull("The intentionally invalid local HLS segment should fail", mounted.failure.get())
            assertTrue("The HLS manifest should be requested", server.awaitRequest("/hls/master.m3u8"))
            assertTrue("The manifest segment should be requested", server.awaitRequest("/hls/segment.ts"))
            compose.waitUntil(timeoutMillis = 3_000) { report.get() != null }

            val reportText = requireNotNull(report.get()).asText()
            assertTrue(reportText.contains("Format: HLS"))
            listOf("127.0.0.1", "url-signature", "segment-signature").forEach {
                assertFalse("Playback report leaked $it", reportText.contains(it, ignoreCase = true))
            }
        } finally {
            unmount(mounted)
        }
    }

    @Test
    fun rangeRequestsReturnTheExactRequestedBytes() {
        val dataSource = newDataSource()
        val start = 97L
        val length = 128L
        try {
            val openedLength = dataSource.open(
                DataSpec.Builder()
                    .setUri(Uri.parse(server.url("/range.mp4")))
                    .setPosition(start)
                    .setLength(length)
                    .build()
            )
            assertEquals(length, openedLength)

            val received = readToEnd(dataSource)
            assertArrayEquals(
                fixtureBytes.copyOfRange(start.toInt(), (start + length).toInt()),
                received
            )
            assertEquals("bytes=$start-${start + length - 1}", server.lastRequest("/range.mp4")?.headers?.get("range"))
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun chunkedMp4WithoutContentLengthStillRendersItsFirstFrame() {
        val dataSource = newDataSource()
        try {
            val unknownLength = dataSource.open(
                DataSpec.Builder().setUri(Uri.parse(server.url("/chunked.mp4"))).build()
            )
            assertEquals(C.LENGTH_UNSET.toLong(), unknownLength)
            assertArrayEquals(fixtureBytes, readToEnd(dataSource))
        } finally {
            dataSource.close()
        }

        val mounted = mountTrailer(server.url("/chunked.mp4"))
        try {
            awaitOutcome(mounted)
            assertTrue("A chunked MP4 with unknown length should render a video frame", mounted.firstFrame.get())
            assertNull(mounted.failure.get())
            assertTrue(server.awaitRequest("/chunked.mp4"))
        } finally {
            unmount(mounted)
        }
    }

    @Test
    fun slowLocalResponseCompletesAndRendersAFrame() {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val mounted = mountTrailer(server.url("/slow.mp4"))
        try {
            awaitOutcome(mounted)
            val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
            assertTrue("The server's delay should be observable", elapsedMs >= LocalTrailerHttpServer.SLOW_RESPONSE_MS)
            assertTrue("The delayed MP4 should still render", mounted.firstFrame.get())
            assertNull(mounted.failure.get())
            assertTrue(server.awaitRequest("/slow.mp4"))
        } finally {
            unmount(mounted)
        }
    }

    @Test
    fun interruptedMp4LoadRaisesAPlaybackFailure() {
        val mounted = mountTrailer(server.url("/interrupted.mp4"))
        try {
            awaitOutcome(mounted)
            assertNotNull("A truncated MP4 response should fail before the first frame", mounted.failure.get())
            assertFalse(mounted.firstFrame.get())
            assertTrue(server.awaitRequest("/interrupted.mp4"))
        } finally {
            unmount(mounted)
        }
    }

    @Test
    fun stoppingTrailerDuringAStalledRequestCancelsThePlaybackOwner() {
        val mounted = mountTrailer(server.url("/blocked.mp4"))
        try {
            assertTrue("The stalled request must reach the local server", server.awaitRequest("/blocked.mp4"))
            val owner = requireNotNull(mounted.pool.activeOwner.value)
            lateinit var player: androidx.media3.exoplayer.ExoPlayer
            instrumentation.runOnMainSync { player = mounted.pool.acquire(owner)!! }
            compose.runOnIdle { mounted.isPlaying.value = false }
            compose.waitUntil(timeoutMillis = 5_000) { mounted.pool.activeOwner.value == null }
            instrumentation.runOnMainSync {
                assertEquals(Player.STATE_IDLE, player.playbackState)
                assertEquals(0, player.mediaItemCount)
            }
            server.releaseBlockedResponse()
            compose.waitForIdle()

            assertNull("Cancelling an in-flight preview should not report a playback failure", mounted.failure.get())
            assertFalse(mounted.firstFrame.get())
        } finally {
            server.releaseBlockedResponse()
            unmount(mounted)
        }
    }

    private fun mountTrailer(
        url: String,
        onFailure: (PlaybackException) -> Unit = {}
    ): MountedTrailer {
        lateinit var pool: TrailerPlayerPool
        instrumentation.runOnMainSync { pool = TrailerPlayerPool(context, forceNative = false) }

        val isPlaying = mutableStateOf(true)
        val firstFrame = AtomicBoolean(false)
        val failure = AtomicReference<PlaybackException?>()
        compose.setContent {
            NuvioTheme(navigationMotion = NavigationMotion.OFF) {
                Box(Modifier.fillMaxSize()) {
                    TrailerPlayer(
                        trailerUrl = url,
                        isPlaying = isPlaying.value,
                        onEnded = {},
                        onFirstFrameRendered = { firstFrame.set(true) },
                        onPlaybackFailure = {
                            failure.set(it)
                            onFailure(it)
                        },
                        modifier = Modifier.fillMaxSize(),
                        trailerPlayerPool = pool
                    )
                }
            }
        }
        return MountedTrailer(pool, isPlaying, firstFrame, failure)
    }

    private fun awaitOutcome(mounted: MountedTrailer) {
        compose.waitUntil(timeoutMillis = PLAYBACK_TIMEOUT_MS) {
            mounted.firstFrame.get() || mounted.failure.get() != null
        }
    }

    private fun unmount(mounted: MountedTrailer) {
        compose.runOnIdle { mounted.isPlaying.value = false }
        compose.waitUntil(timeoutMillis = 5_000) { mounted.pool.activeOwner.value == null }
        instrumentation.runOnMainSync { mounted.pool.release() }
    }

    private fun newDataSource(): DefaultHttpDataSource = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(2_000)
        .setReadTimeoutMs(4_000)
        .createDataSource()

    private fun readToEnd(dataSource: DefaultHttpDataSource): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = dataSource.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            result.write(buffer, 0, read)
        }
        return result.toByteArray()
    }

    private data class MountedTrailer(
        val pool: TrailerPlayerPool,
        val isPlaying: MutableState<Boolean>,
        val firstFrame: AtomicBoolean,
        val failure: AtomicReference<PlaybackException?>
    )

    private companion object {
        const val PLAYBACK_TIMEOUT_MS = 12_000L
    }
}

private class LocalTrailerHttpServer(private val mp4: ByteArray) : Closeable {
    private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val accepting = AtomicBoolean(true)
    private val workers = Executors.newCachedThreadPool()
    private val sockets = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    private val blockedResponse = CountDownLatch(1)
    private val requests = CopyOnWriteArrayList<ObservedRequest>()
    private val acceptThread = Thread({ acceptConnections() }, "trailer-matrix-http-accept").apply {
        isDaemon = true
        start()
    }

    fun url(pathAndQuery: String): String = "http://127.0.0.1:${serverSocket.localPort}$pathAndQuery"

    fun awaitRequest(path: String, timeoutMs: Long = 5_000): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (requests.any { it.path == path }) return true
            Thread.sleep(20)
        }
        return requests.any { it.path == path }
    }

    fun lastRequest(path: String): ObservedRequest? = requests.lastOrNull { it.path == path }

    fun releaseBlockedResponse() {
        blockedResponse.countDown()
    }

    override fun close() {
        accepting.set(false)
        releaseBlockedResponse()
        runCatching { serverSocket.close() }
        sockets.toList().forEach { runCatching { it.close() } }
        workers.shutdownNow()
        runCatching { acceptThread.join(1_000) }
        runCatching { workers.awaitTermination(2, TimeUnit.SECONDS) }
    }

    private fun acceptConnections() {
        while (accepting.get()) {
            try {
                val socket = serverSocket.accept()
                sockets.add(socket)
                workers.execute {
                    try {
                        handle(socket)
                    } finally {
                        sockets.remove(socket)
                        runCatching { socket.close() }
                    }
                }
            } catch (_: SocketException) {
                if (accepting.get()) return
            } catch (_: IOException) {
                if (accepting.get()) return
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 5_000
        val input = BufferedInputStream(socket.getInputStream())
        val firstLine = readLine(input) ?: return
        val requestParts = firstLine.split(' ', limit = 3)
        if (requestParts.size < 2) return
        val target = requestParts[1]
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val delimiter = line.indexOf(':')
            if (delimiter > 0) headers[line.substring(0, delimiter).trim().lowercase()] = line.substring(delimiter + 1).trim()
        }
        val path = runCatching { URI.create(target).rawPath.orEmpty() }.getOrDefault(target.substringBefore('?'))
        val request = ObservedRequest(requestParts[0], target, path, headers)
        requests += request

        when (path) {
            "/hls/master.m3u8" -> writeFixed(
                socket,
                request,
                ("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:1\n" +
                    "#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:1.0,\n" +
                    "segment.ts?signature=segment-signature\n#EXT-X-ENDLIST\n")
                    .toByteArray(StandardCharsets.UTF_8),
                "application/vnd.apple.mpegurl"
            )
            "/hls/segment.ts" -> writeFixed(
                socket,
                request,
                ByteArray(188 * 5) { 0 },
                "video/mp2t"
            )
            "/chunked.mp4" -> writeChunked(socket, request)
            "/slow.mp4" -> {
                Thread.sleep(SLOW_RESPONSE_MS)
                writeMp4(socket, request)
            }
            "/interrupted.mp4" -> writeTruncatedMp4(socket, request)
            "/blocked.mp4" -> {
                if (blockedResponse.await(10, TimeUnit.SECONDS) && !socket.isClosed) writeMp4(socket, request)
            }
            else -> writeMp4(socket, request)
        }
    }

    private fun writeMp4(socket: Socket, request: ObservedRequest) {
        val range = rangeFor(request, mp4.size) ?: run {
            writeFixed(socket, request, mp4, "video/mp4")
            return
        }
        val selected = mp4.copyOfRange(range.first, range.last + 1)
        writeFixed(
            socket,
            request,
            selected,
            "video/mp4",
            status = 206,
            contentRange = "bytes ${range.first}-${range.last}/${mp4.size}"
        )
    }

    private fun writeChunked(socket: Socket, request: ObservedRequest) {
        val range = rangeFor(request, mp4.size)
        val body = if (range == null) mp4 else mp4.copyOfRange(range.first, range.last + 1)
        val out = BufferedOutputStream(socket.getOutputStream())
        out.write("HTTP/1.1 ${if (range == null) 200 else 206} ${if (range == null) "OK" else "Partial Content"}\r\n".toByteArray(StandardCharsets.US_ASCII))
        out.write("Content-Type: video/mp4\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n".toByteArray(StandardCharsets.US_ASCII))
        if (range != null) out.write("Content-Range: bytes ${range.first}-${range.last}/${mp4.size}\r\n".toByteArray(StandardCharsets.US_ASCII))
        out.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
        body.toList().chunked(1024).forEach { chunk ->
            val bytes = chunk.toByteArray()
            out.write("${bytes.size.toString(16)}\r\n".toByteArray(StandardCharsets.US_ASCII))
            out.write(bytes)
            out.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
        }
        out.write("0\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        out.flush()
    }

    private fun writeTruncatedMp4(socket: Socket, request: ObservedRequest) {
        val range = rangeFor(request, mp4.size)
        val body = if (range == null) mp4 else mp4.copyOfRange(range.first, range.last + 1)
        val sent = minOf(body.size, 32)
        writeFixed(
            socket,
            request,
            body,
            "video/mp4",
            declaredLength = body.size.toLong() + 4_096L,
            bytesToWrite = sent,
            status = if (range == null) 200 else 206,
            contentRange = range?.let { "bytes ${it.first}-${it.last}/${mp4.size}" }
        )
    }

    private fun writeFixed(
        socket: Socket,
        request: ObservedRequest,
        body: ByteArray,
        contentType: String,
        declaredLength: Long = body.size.toLong(),
        bytesToWrite: Int = body.size,
        status: Int = 200,
        contentRange: String? = null
    ) {
        val phrase = if (status == 206) "Partial Content" else "OK"
        val out = BufferedOutputStream(socket.getOutputStream())
        out.write("HTTP/1.1 $status $phrase\r\n".toByteArray(StandardCharsets.US_ASCII))
        out.write("Content-Type: $contentType\r\nContent-Length: $declaredLength\r\nConnection: close\r\n".toByteArray(StandardCharsets.US_ASCII))
        if (contentRange != null) out.write("Content-Range: $contentRange\r\n".toByteArray(StandardCharsets.US_ASCII))
        out.write("\r\n".toByteArray(StandardCharsets.US_ASCII))
        if (bytesToWrite > 0) out.write(body, 0, bytesToWrite)
        out.flush()
    }

    private fun rangeFor(request: ObservedRequest, size: Int): IntRange? {
        val value = request.headers["range"] ?: return null
        val match = Regex("bytes=(\\d+)-(\\d*)").matchEntire(value) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: (size - 1)
        if (start !in 0 until size || end < start) return null
        return start..minOf(end, size - 1)
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) return if (bytes.size() == 0) null else bytes.toString(StandardCharsets.ISO_8859_1.name())
            if (next == '\n'.code) break
            if (next != '\r'.code) bytes.write(next)
        }
        return bytes.toString(StandardCharsets.ISO_8859_1.name())
    }

    data class ObservedRequest(
        val method: String,
        val target: String,
        val path: String,
        val headers: Map<String, String>
    )

    companion object {
        const val SLOW_RESPONSE_MS = 350L
    }
}
