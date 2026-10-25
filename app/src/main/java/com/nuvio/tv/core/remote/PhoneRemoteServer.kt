package com.nuvio.tv.core.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.intercept
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Local-only Phone Remote transport. Callers must resolve a private LAN address and start it only
 * after the user explicitly enables pairing. The listener binds to that address instead of all
 * interfaces and requires exact Host and Origin matches.
 */
internal class PhoneRemoteServer(
    private val bindAddress: String,
    private val requestedPort: Int = 0,
    private val pairingAuthority: PhoneRemotePairingAuthority = PhoneRemotePairingAuthority(),
    private val onPairingRequested: (PhoneRemotePairingAuthority.PendingPhonePairing) -> Unit,
    private val onCommand: (PhoneRemoteCommand) -> Unit
) : Closeable {
    init {
        require(isAllowedBindAddress(bindAddress)) {
            "Phone Remote must bind to a private IPv4 or loopback address"
        }
        require(requestedPort == 0 || requestedPort in 1..65_535) {
            "Phone Remote port must be 0 or a valid TCP port"
        }
    }

    private val sockets = ConcurrentHashMap.newKeySet<RemoteSocket>()
    private val pairingSockets = ConcurrentHashMap<String, RemoteSocket>()

    @Volatile
    private var engine: EmbeddedServer<*, *>? = null

    @Volatile
    private var serverScope: CoroutineScope? = null

    @Volatile
    private var activePort: Int = -1

    suspend fun startRemote(): String = withContext(Dispatchers.IO) {
        if (activePort > 0) return@withContext baseUrl()
        val port = if (requestedPort == 0) reserveEphemeralPort() else requestedPort
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val candidate = scope.embeddedServer(CIO, host = bindAddress, port = port) {
            install(WebSockets) {
                maxFrameSize = MAX_MESSAGE_BYTES.toLong()
            }
            routing {
                get(PAGE_PATH) {
                    if (!hasExpectedHost(call.request.headers[HttpHeaders.Host])) {
                        call.respondText("Forbidden", ContentType.Text.Plain, io.ktor.http.HttpStatusCode.Forbidden)
                        return@get
                    }
                    call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                    call.response.headers.append("X-Content-Type-Options", "nosniff")
                    call.response.headers.append("Referrer-Policy", "no-referrer")
                    call.response.headers.append(
                        "Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; " +
                            "connect-src 'self' ws://$bindAddress:$activePort; base-uri 'none'; " +
                            "form-action 'none'; frame-ancestors 'none'"
                    )
                    call.respondText(REMOTE_PAGE, ContentType.Text.Html)
                }
                route(WEBSOCKET_PATH) {
                    intercept(ApplicationCallPipeline.Call) {
                        if (!hasExpectedHost(context.request.headers[HttpHeaders.Host]) ||
                            context.request.headers[HttpHeaders.Origin]
                                ?.equals(expectedOrigin(), ignoreCase = true) != true
                        ) {
                            context.respondText("Forbidden", ContentType.Text.Plain, HttpStatusCode.Forbidden)
                            finish()
                        }
                    }
                    webSocket {
                        if (sockets.size >= MAX_CONNECTIONS) {
                            closeAndFinish(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Too many connections"))
                            return@webSocket
                        }
                        val connection = RemoteSocket(this, call.request.local.remoteHost)
                        sockets += connection
                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        if (!frame.fin || frame.data.size > MAX_MESSAGE_BYTES) {
                                            closeAndFinish(CloseReason(CloseReason.Codes.TOO_BIG, "Message too large"))
                                            break
                                        }
                                        if (!connection.handleText(frame.readText())) {
                                            closeAndFinish(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Message rejected"))
                                            break
                                        }
                                    }
                                    is Frame.Close -> break
                                    else -> {
                                        closeAndFinish(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Text frames required"))
                                        break
                                    }
                                }
                            }
                        } finally {
                            sockets -= connection
                            connection.onClosed()
                        }
                    }
                }
            }
        }
        try {
            candidate.start(wait = false)
            engine = candidate
            serverScope = scope
            activePort = port
            baseUrl()
        } catch (error: Throwable) {
            candidate.stop(gracePeriodMillis = 0, timeoutMillis = 250)
            scope.cancel()
            throw error
        }
    }

    fun openPairingWindow(): PhoneRemotePairingAuthority.PairingChallenge =
        pairingAuthority.openPairingWindow()

    fun pendingPairing(): PhoneRemotePairingAuthority.PendingPhonePairing? =
        pairingAuthority.currentPendingRequest()

    fun approvePairing(requestId: String): PhoneRemotePairingAuthority.PairingApprovalResult {
        val result = pairingAuthority.approve(requestId)
        if (result is PhoneRemotePairingAuthority.PairingApprovalResult.Approved) {
            val socket = pairingSockets.remove(requestId)
            if (socket == null || !socket.sendPaired(result.sessionToken, result.expiresAtElapsedMs)) {
                pairingAuthority.revoke()
                return PhoneRemotePairingAuthority.PairingApprovalResult.NoPendingRequest
            }
        }
        return result
    }

    fun rejectPairing(requestId: String): Boolean {
        val rejected = pairingAuthority.reject(requestId)
        if (rejected) pairingSockets.remove(requestId)?.rejectPairing()
        return rejected
    }

    fun revokeAllPairings(): Boolean {
        val revoked = pairingAuthority.revoke()
        pairingSockets.clear()
        sockets.toList().forEach(RemoteSocket::revoke)
        return revoked
    }

    @Synchronized
    fun stopRemote() {
        revokeAllPairings()
        engine?.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
        engine = null
        serverScope?.cancel()
        serverScope = null
        activePort = -1
    }

    override fun close() = stopRemote()

    private fun hasExpectedHost(host: String?): Boolean =
        host?.equals(expectedAuthority(), ignoreCase = true) == true

    private suspend fun WebSocketServerSession.closeAndFinish(reason: CloseReason) {
        outgoing.send(Frame.Close(reason))
        outgoing.close()
    }

    private fun expectedAuthority(): String = "$bindAddress:$activePort"

    private fun expectedOrigin(): String = "http://${expectedAuthority()}"

    private fun baseUrl(): String = expectedOrigin()

    private fun reserveEphemeralPort(): Int {
        val address = InetAddress.getByAddress(parseIpv4(bindAddress))
        return ServerSocket(0, 1, address).use { it.localPort }
    }

    private fun isAllowedBindAddress(value: String): Boolean {
        val address = parseIpv4(value) ?: return false
        val firstOctet = address[0].toInt() and 0xff
        return firstOctet == 127 || PhoneRemoteLanAddress.isPrivateIpv4(address)
    }

    private fun parseIpv4(value: String): ByteArray? {
        val octets = value.split('.')
        if (octets.size != 4) return null
        val parsed = octets.map { it.toIntOrNull()?.takeIf { octet -> octet in 0..255 } ?: return null }
        return ByteArray(4) { index -> parsed[index].toByte() }
    }

    private inner class RemoteSocket(
        private val socket: WebSocketServerSession,
        private val peerAddress: String
    ) {
        @Volatile
        private var sessionToken: String? = null

        @Volatile
        private var pairingRequestId: String? = null

        private var commandWindowStartedAt = 0L
        private var commandCountInWindow = 0

        fun sendPaired(token: String, expiresAtElapsedMs: Long): Boolean {
            sessionToken = token
            pairingRequestId = null
            return sendJson(
                jsonObject(
                    "type" to TYPE_PAIRED,
                    "token" to token,
                    "expiresAtElapsedMs" to expiresAtElapsedMs
                )
            )
        }

        fun rejectPairing() {
            sendJson(jsonObject("type" to TYPE_REJECTED))
            pairingRequestId = null
        }

        fun revoke() {
            sessionToken = null
            sendJson(jsonObject("type" to TYPE_REVOKED))
            socket.outgoing.close()
        }

        suspend fun handleText(payload: String): Boolean {
            if (!allowCommandNow()) {
                sendError("Rate limited")
                return true
            }
            if (payload.length > MAX_MESSAGE_LENGTH) return rejectMessage("Message too large")
            val root = runCatching { JsonParser.parseString(payload) }.getOrNull()
            if (root == null || !root.isJsonObject) return rejectMessage("Invalid message")
            val json = root.asJsonObject
            return when {
                sessionToken != null -> handleAuthenticated(json)
                json.stringValue("type") == TYPE_AUTH -> handleReconnect(json)
                json.stringValue("type") == TYPE_PAIR -> handlePairRequest(json)
                else -> {
                    sendError("Pairing required")
                    true
                }
            }
        }

        fun onClosed() {
            pairingRequestId?.let { requestId ->
                pairingSockets.remove(requestId, this)
                pairingAuthority.reject(requestId)
            }
        }

        private fun handlePairRequest(json: JsonObject): Boolean {
            if (pairingRequestId != null) return rejectMessage("Pairing request already sent")
            val result = pairingAuthority.requestPairing(
                code = json.stringValue("code").orEmpty(),
                deviceName = json.stringValue("deviceName"),
                peerAddress = peerAddress
            )
            when (result) {
                is PhoneRemotePairingAuthority.PairingRequestResult.WaitingForApproval -> {
                    pairingRequestId = result.request.requestId
                    pairingSockets[result.request.requestId] = this
                    if (!sendJson(jsonObject("type" to TYPE_PENDING))) {
                        pairingAuthority.reject(result.request.requestId)
                        pairingSockets.remove(result.request.requestId, this)
                        pairingRequestId = null
                        return rejectMessage("Approval unavailable")
                    }
                    val callbackSucceeded = runCatching { onPairingRequested(result.request) }.isSuccess
                    if (!callbackSucceeded) {
                        pairingAuthority.reject(result.request.requestId)
                        pairingSockets.remove(result.request.requestId, this)
                        pairingRequestId = null
                        sendError("Approval unavailable")
                    }
                    return true
                }
                is PhoneRemotePairingAuthority.PairingRequestResult.InvalidCode ->
                    sendError("Invalid code; ${result.attemptsRemaining} attempts remain")
                PhoneRemotePairingAuthority.PairingRequestResult.TooManyAttempts ->
                    return rejectMessage("Too many attempts")
                PhoneRemotePairingAuthority.PairingRequestResult.RequestAlreadyPending ->
                    sendError("A pairing request is already waiting for approval")
                PhoneRemotePairingAuthority.PairingRequestResult.Expired ->
                    return rejectMessage("Pairing window expired")
            }
            return true
        }

        private fun handleReconnect(json: JsonObject): Boolean {
            val token = json.stringValue("token").orEmpty()
            if (token.length > MAX_TOKEN_LENGTH || !pairingAuthority.isAuthorized(token)) {
                return rejectMessage("Invalid or expired session")
            }
            sessionToken = token
            sendJson(jsonObject("type" to TYPE_READY))
            return true
        }

        private fun handleAuthenticated(json: JsonObject): Boolean {
            val token = sessionToken ?: return rejectMessage("Session required")
            if (!pairingAuthority.isAuthorized(token)) return rejectMessage("Session expired")
            when (json.stringValue("type")) {
                TYPE_COMMAND -> {
                    val action = parseAction(json.stringValue("action"))
                        ?: run {
                            sendError("Unsupported command")
                            return true
                        }
                    runCatching { onCommand(PhoneRemoteCommand.Action(action)) }
                        .onFailure { sendError("Command unavailable") }
                }
                TYPE_TEXT -> {
                    val text = json.stringValue("text")
                    if (text == null || text.length > MAX_KEYBOARD_TEXT_LENGTH) {
                        sendError("Text input is invalid or too long")
                    } else {
                        val filtered = text.filterNot(Char::isISOControl)
                        if (filtered.isNotEmpty()) {
                            runCatching { onCommand(PhoneRemoteCommand.Text(filtered)) }
                                .onFailure { sendError("Text input unavailable") }
                        }
                    }
                }
                else -> sendError("Unsupported message")
            }
            return true
        }

        private fun allowCommandNow(): Boolean {
            val now = System.nanoTime() / NANOS_PER_MILLISECOND
            if (now - commandWindowStartedAt >= COMMAND_RATE_WINDOW_MS) {
                commandWindowStartedAt = now
                commandCountInWindow = 0
            }
            if (commandCountInWindow >= MAX_COMMANDS_PER_WINDOW) return false
            commandCountInWindow += 1
            return true
        }

        private fun rejectMessage(reason: String): Boolean {
            sendError(reason.take(MAX_ERROR_MESSAGE_LENGTH))
            return true
        }

        private fun sendError(message: String) =
            sendJson(jsonObject("type" to TYPE_ERROR, "message" to message))

        private fun sendJson(json: JsonObject): Boolean =
            socket.outgoing.trySend(Frame.Text(json.toString())).isSuccess
    }

    private fun jsonObject(vararg values: Pair<String, Any?>): JsonObject = JsonObject().apply {
        values.forEach { (key, value) ->
            when (value) {
                null -> add(key, null)
                is String -> addProperty(key, value)
                is Number -> addProperty(key, value)
                is Boolean -> addProperty(key, value)
                else -> addProperty(key, value.toString())
            }
        }
    }

    private fun JsonObject.stringValue(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun parseAction(name: String?): PhoneRemoteCommand.ActionType? = when (name) {
        "up" -> PhoneRemoteCommand.ActionType.UP
        "down" -> PhoneRemoteCommand.ActionType.DOWN
        "left" -> PhoneRemoteCommand.ActionType.LEFT
        "right" -> PhoneRemoteCommand.ActionType.RIGHT
        "select" -> PhoneRemoteCommand.ActionType.SELECT
        "back" -> PhoneRemoteCommand.ActionType.BACK
        "home" -> PhoneRemoteCommand.ActionType.HOME
        "play_pause" -> PhoneRemoteCommand.ActionType.PLAY_PAUSE
        "seek_backward" -> PhoneRemoteCommand.ActionType.SEEK_BACKWARD
        "seek_forward" -> PhoneRemoteCommand.ActionType.SEEK_FORWARD
        "volume_down" -> PhoneRemoteCommand.ActionType.VOLUME_DOWN
        "volume_up" -> PhoneRemoteCommand.ActionType.VOLUME_UP
        "toggle_subtitles" -> PhoneRemoteCommand.ActionType.TOGGLE_SUBTITLES
        else -> null
    }

    private companion object {
        const val PAGE_PATH = "/"
        const val WEBSOCKET_PATH = "/ws"
        const val MAX_CONNECTIONS = 2
        const val MAX_MESSAGE_BYTES = 2_048
        const val MAX_MESSAGE_LENGTH = 2_048
        const val MAX_TOKEN_LENGTH = 64
        const val MAX_KEYBOARD_TEXT_LENGTH = 256
        const val COMMAND_RATE_WINDOW_MS = 1_000L
        const val MAX_COMMANDS_PER_WINDOW = 30
        const val MAX_ERROR_MESSAGE_LENGTH = 120
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val TYPE_PAIR = "pair"
        const val TYPE_AUTH = "auth"
        const val TYPE_PAIRED = "paired"
        const val TYPE_PENDING = "pairing_pending"
        const val TYPE_REJECTED = "rejected"
        const val TYPE_REVOKED = "revoked"
        const val TYPE_READY = "ready"
        const val TYPE_COMMAND = "command"
        const val TYPE_TEXT = "text"
        const val TYPE_ERROR = "error"

        val REMOTE_PAGE = """
            <!doctype html>
            <html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Controle Telumia</title><style>
            *{box-sizing:border-box}body{margin:0;background:#11131a;color:#f5f5f7;font:16px system-ui,sans-serif;min-height:100vh;display:grid;place-items:center;padding:20px}
            main{width:min(100%,420px);background:#1b1e28;border:1px solid #343948;border-radius:24px;padding:24px;box-shadow:0 20px 70px #0008}
            h1{font-size:1.4rem;margin:0 0 6px}p{color:#b7bdca;margin:0 0 18px}.status{min-height:24px;color:#9ecbff;margin:10px 0 18px}
            input,button{font:inherit;border:0;border-radius:14px;padding:14px;color:#fff}input{width:100%;background:#10121a;border:1px solid #454b5d;margin-bottom:10px}button{background:#343949;cursor:pointer;touch-action:manipulation}button:active{background:#59647d;transform:scale(.98)}
            .primary{width:100%;background:#7864e8}.controls{display:none}.pad{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:16px 0}.pad button{min-height:58px;font-size:1.2rem}.actions{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:14px 0}.keyboard{display:flex;gap:8px}.keyboard input{margin:0}.keyboard button{flex:none}
            </style></head><body><main><h1>Controle Telumia</h1><p>Conecte-se à mesma rede local do aparelho.</p>
            <section id="pair"><label for="code">Digite o código exibido na TV</label><input id="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="000000"><button class="primary" id="connect">Solicitar pareamento</button></section>
            <div class="status" id="status" role="status" aria-live="polite">Conectando ao aparelho…</div>
            <section class="controls" id="controls"><div class="pad">
            <span></span><button data-action="up" aria-label="Cima">↑</button><span></span>
            <button data-action="left" aria-label="Esquerda">←</button><button data-action="select">OK</button><button data-action="right" aria-label="Direita">→</button>
            <span></span><button data-action="down" aria-label="Baixo">↓</button><span></span></div>
            <div class="actions"><button data-action="back">Voltar</button><button data-action="home">Início</button><button data-action="play_pause">Pausar/Play</button>
            <button data-action="seek_backward">−10 s</button><button data-action="toggle_subtitles">Legendas</button><button data-action="seek_forward">+10 s</button>
            <button data-action="volume_down">Volume −</button><button data-action="volume_up">Volume +</button></div>
            <form class="keyboard" id="keyboard"><input id="text" maxlength="256" autocomplete="off" placeholder="Enviar texto"><button type="submit">Enviar</button></form></section>
            </main><script>
            const statusNode=document.querySelector('#status'),pair=document.querySelector('#pair'),controls=document.querySelector('#controls');const initialCode=new URLSearchParams(location.search).get('code')||'';let socket,token=initialCode?'':(sessionStorage.getItem('telumia_remote_token')||''),pairingRequested=false,reconnectCount=0;
            const say=(text)=>statusNode.textContent=text;const send=(value)=>{if(socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify(value))};
            function connect(){socket=new WebSocket(`ws://${'$'}{location.host}/ws`);socket.onopen=()=>{reconnectCount=0;if(token)send({type:'auth',token});else if(initialCode&&!pairingRequested){document.querySelector('#code').value=initialCode;pairingRequested=true;send({type:'pair',code:initialCode,deviceName:'Celular'})}else say('Digite o código mostrado na TV para pedir pareamento.')};socket.onclose=()=>{if(token&&reconnectCount<30){reconnectCount++;controls.style.display='none';pair.style.display='block';say('Reconectando…');setTimeout(connect,1000)}else{token='';sessionStorage.removeItem('telumia_remote_token');controls.style.display='none';pair.style.display='block';say('Conexão encerrada. Escaneie o QR Code para parear novamente.')}};socket.onerror=()=>say('Não foi possível conectar ao aparelho.');socket.onmessage=(event)=>{let data;try{data=JSON.parse(event.data)}catch{return}if(data.type==='pairing_pending')say('Pedido enviado. Confirme o celular na TV.');else if(data.type==='paired'){token=data.token;sessionStorage.setItem('telumia_remote_token',token);pair.style.display='none';controls.style.display='block';say('Controle conectado.');}else if(data.type==='ready'){pair.style.display='none';controls.style.display='block';say('Controle conectado.')}else if(data.type==='rejected')say('Pareamento recusado na TV.');else if(data.type==='revoked'){token='';sessionStorage.removeItem('telumia_remote_token');controls.style.display='none';pair.style.display='block';say('Pareamento revogado na TV.')}else if(data.type==='error'){say(data.message);if(token&&/invalid|expired/i.test(data.message)){token='';sessionStorage.removeItem('telumia_remote_token');controls.style.display='none';pair.style.display='block';}}}}
            document.querySelector('#connect').onclick=()=>{const code=document.querySelector('#code').value.trim();if(!/^\d{6}$/.test(code)){say('Digite os seis números exibidos na TV.');return}send({type:'pair',code,deviceName:'Celular'})};
            document.querySelectorAll('[data-action]').forEach(button=>button.addEventListener('click',()=>send({type:'command',action:button.dataset.action})));
            document.querySelector('#keyboard').addEventListener('submit',event=>{event.preventDefault();const field=document.querySelector('#text');if(field.value){send({type:'text',text:field.value});field.value=''}});
            connect();
            </script></body></html>
        """.trimIndent()
    }
}

internal sealed interface PhoneRemoteCommand {
    data class Action(val action: ActionType) : PhoneRemoteCommand
    data class Text(val value: String) : PhoneRemoteCommand

    enum class ActionType {
        UP, DOWN, LEFT, RIGHT, SELECT, BACK, HOME,
        PLAY_PAUSE, SEEK_BACKWARD, SEEK_FORWARD,
        VOLUME_DOWN, VOLUME_UP, TOGGLE_SUBTITLES
    }
}
