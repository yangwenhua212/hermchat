package com.eraherm.hermchat.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class WsProtocol {
    JSON_RPC,
    AGENT_MESSAGE,
    SIMPLE,
}

/**
 * Hermes / custom WebSocket bridge with multi-protocol decode.
 * See docs/BRIDGE_PROTOCOL.md.
 */
class HermesBridgeClient(
    private val endpoint: String,
    private val preferred: WsProtocol = detectProtocol(endpoint),
    private val client: OkHttpClient = defaultClient(),
) : StreamingChatClient {

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val mutex = Mutex()
    private var webSocket: WebSocket? = null
    private var sessionId: String? = null
    private var protocol: WsProtocol = preferred

    private val rpcWaiters = ConcurrentHashMap<String, (Result<JSONObject>) -> Unit>()
    private val streamHandlers = ConcurrentHashMap<String, (BridgeStreamEvent) -> Unit>()
    private val rpcSeq = AtomicLong(1)

    private val intentionalClose = AtomicBoolean(false)
    private val rootJob = SupervisorJob()
    private val scope = CoroutineScope(rootJob + Dispatchers.IO)
    private var reconnectJob: Job? = null

    override suspend fun ensureConnected() {
        intentionalClose.set(false)
        mutex.withLock {
            if (_connected.value && webSocket != null) return
            openSocketLocked(retries = 3)
        }
    }

    override fun streamChat(
        prompt: String,
        history: List<ChatTurn>,
        imageDataUrl: String?,
    ): Flow<String> = callbackFlow {
        ensureConnected()
        if (protocol == WsProtocol.JSON_RPC && sessionId.isNullOrBlank()) {
            runCatching { bootstrapSession() }.onFailure {
                protocol = WsProtocol.SIMPLE
            }
        }
        val requestId = UUID.randomUUID().toString()
        val handler: (BridgeStreamEvent) -> Unit = { event ->
            when (event) {
                is BridgeStreamEvent.Delta -> trySend(event.text)
                is BridgeStreamEvent.Done -> close()
                is BridgeStreamEvent.Error -> close(Exception(event.message))
            }
        }
        streamHandlers[requestId] = handler

        val sent = when (protocol) {
            WsProtocol.JSON_RPC -> sendJsonRpcPrompt(requestId, prompt, imageDataUrl)
            WsProtocol.AGENT_MESSAGE -> sendAgentMessage(requestId, prompt, imageDataUrl)
            WsProtocol.SIMPLE -> sendSimpleChat(requestId, prompt, imageDataUrl)
        }
        if (!sent) {
            streamHandlers.remove(requestId)
            close(IllegalStateException("WebSocket 未连接"))
            return@callbackFlow
        }

        awaitClose { streamHandlers.remove(requestId) }
    }

    override fun sendToolResult(toolCallId: String, ok: Boolean, message: String) {
        val payload = JSONObject()
            .put("type", "tool_result")
            .put("id", toolCallId)
            .put("ok", ok)
            .put("content", message)
            .put("message", message)
        webSocket?.send(payload.toString())
    }

    override fun resetConversation() {
        sessionId = null
    }

    override fun close() {
        intentionalClose.set(true)
        reconnectJob?.cancel()
        reconnectJob = null
        rootJob.cancel()
        webSocket?.close(1000, "client close")
        webSocket = null
        _connected.value = false
        sessionId = null
        rpcWaiters.clear()
        streamHandlers.clear()
    }

    private suspend fun openSocketLocked(retries: Int = 1) {
        var lastError: Throwable? = null
        repeat(retries.coerceAtLeast(1)) { attempt ->
            if (attempt > 0) delay(500L * attempt)
            try {
                connectOnceLocked()
                return
            } catch (t: Throwable) {
                lastError = t
                _connected.value = false
                webSocket = null
            }
        }
        throw lastError ?: IllegalStateException("WebSocket 连接失败")
    }

    private suspend fun connectOnceLocked() {
        val request = Request.Builder().url(endpoint.trim()).build()
        withTimeout(8_000) {
            suspendCancellableCoroutine { cont ->
                val socket = client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            this@HermesBridgeClient.webSocket = webSocket
                            _connected.value = true
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            handleIncoming(text)
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            _connected.value = false
                            this@HermesBridgeClient.webSocket = null
                            failAll(t)
                            if (cont.isActive) {
                                cont.resumeWithException(t)
                            } else {
                                scheduleReconnect()
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            _connected.value = false
                            this@HermesBridgeClient.webSocket = null
                            if (!intentionalClose.get() && code != 1000) {
                                scheduleReconnect()
                            }
                        }
                    },
                )
                cont.invokeOnCancellation { socket.cancel() }
            }
        }

        if (protocol == WsProtocol.JSON_RPC && sessionId.isNullOrBlank()) {
            runCatching { bootstrapSession() }.onFailure {
                protocol = WsProtocol.SIMPLE
            }
        }
    }

    private fun scheduleReconnect() {
        if (intentionalClose.get()) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var delayMs = 1_000L
            repeat(6) {
                if (intentionalClose.get() || !isActive) return@launch
                if (_connected.value && webSocket != null) return@launch
                delay(delayMs)
                runCatching {
                    mutex.withLock {
                        if (_connected.value && webSocket != null) return@withLock
                        openSocketLocked(retries = 1)
                    }
                }
                if (_connected.value) return@launch
                delayMs = (delayMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun bootstrapSession() {
        val result = rpcRequest("session.create", JSONObject())
        sessionId = result.optString("session_id").ifEmpty {
            result.optJSONObject("session")?.optString("id")
        }
        if (sessionId.isNullOrBlank()) {
            error("session.create 未返回 session_id")
        }
    }

    private suspend fun rpcRequest(method: String, params: JSONObject): JSONObject {
        val id = "r${rpcSeq.getAndIncrement()}"
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        return withTimeout(8_000) {
            suspendCancellableCoroutine { cont ->
                rpcWaiters[id] = { result ->
                    result.fold(
                        onSuccess = { if (cont.isActive) cont.resume(it) },
                        onFailure = { if (cont.isActive) cont.resumeWithException(it) },
                    )
                }
                val ok = webSocket?.send(payload.toString()) == true
                if (!ok) {
                    rpcWaiters.remove(id)
                    cont.resumeWithException(IllegalStateException("发送失败"))
                }
                cont.invokeOnCancellation { rpcWaiters.remove(id) }
            }
        }
    }

    private fun sendJsonRpcPrompt(
        requestId: String,
        prompt: String,
        imageDataUrl: String? = null,
    ): Boolean {
        val sid = sessionId
        val params = JSONObject()
            .put("text", prompt)
            .put("content", prompt)
        if (!sid.isNullOrBlank()) params.put("session_id", sid)
        params.put("client_request_id", requestId)
        putAttachment(params, imageDataUrl)
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", requestId)
            .put("method", "prompt.submit")
            .put("params", params)
        rpcWaiters[requestId] = { result ->
            result.onFailure { err ->
                streamHandlers.remove(requestId)?.invoke(
                    BridgeStreamEvent.Error(
                        com.eraherm.hermchat.util.UserFacingError.of(err, "请求失败"),
                    ),
                )
            }
        }
        return webSocket?.send(payload.toString()) == true
    }

    private fun sendAgentMessage(
        requestId: String,
        prompt: String,
        imageDataUrl: String? = null,
    ): Boolean {
        val payload = JSONObject()
            .put("type", "agent.message.send")
            .put("id", requestId)
            .put("content", prompt)
            .put("text", prompt)
        putAttachment(payload, imageDataUrl)
        return webSocket?.send(payload.toString()) == true
    }

    private fun sendSimpleChat(
        requestId: String,
        prompt: String,
        imageDataUrl: String? = null,
    ): Boolean {
        val payload = JSONObject()
            .put("type", "chat")
            .put("id", requestId)
            .put("content", prompt)
        putAttachment(payload, imageDataUrl)
        return webSocket?.send(payload.toString()) == true
    }

    /** Bridge 简易附件：inline base64（见 BRIDGE_PROTOCOL.md）。 */
    private fun putAttachment(target: JSONObject, imageDataUrl: String?) {
        val raw = imageDataUrl?.trim().orEmpty()
        if (raw.isBlank()) return
        val comma = raw.indexOf(',')
        val meta = if (raw.startsWith("data:", ignoreCase = true) && comma > 0) {
            raw.substring(5, comma)
        } else {
            "image/jpeg;base64"
        }
        val data = if (comma > 0) raw.substring(comma + 1) else raw
        if (data.isBlank()) return
        val mime = meta.substringBefore(';').ifBlank { "image/jpeg" }
        val ext = when {
            mime.contains("png", true) -> "png"
            mime.contains("webp", true) -> "webp"
            mime.contains("gif", true) -> "gif"
            else -> "jpg"
        }
        target.put(
            "attachment",
            JSONObject()
                .put("name", "image.$ext")
                .put("mime", mime)
                .put("encoding", "base64")
                .put("data", data),
        )
    }

    private fun handleIncoming(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return

        if (json.has("id") && (json.has("result") || json.has("error")) && !json.has("method")) {
            val id = json.opt("id")?.toString() ?: return
            val waiter = rpcWaiters.remove(id)
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                waiter?.invoke(
                    Result.failure(
                        Exception(err?.optString("message") ?: "RPC error"),
                    ),
                )
            } else {
                val result = json.optJSONObject("result") ?: JSONObject()
                waiter?.invoke(Result.success(result))
            }
            return
        }

        val method = json.optString("method").ifEmpty {
            json.optString("event").ifEmpty { json.optString("type") }
        }
        val params = json.optJSONObject("params")
            ?: json.optJSONObject("payload")
            ?: json

        when {
            method == "message.delta" || method.endsWith("message.delta") ||
                method == "token" || method == "delta" || method == "agent.message.delta" -> {
                val delta = extractText(params) ?: extractText(json) ?: return
                dispatchDelta(json, params, delta)
            }

            method == "message.complete" || method == "done" ||
                method == "agent.message.done" -> {
                dispatchDone(json, params)
            }

            method == "error" || method == "agent.message.error" -> {
                val message = params.optString("message")
                    .ifEmpty { json.optString("message") }
                    .ifEmpty { "Agent 错误" }
                dispatchError(json, params, message)
            }
        }
    }

    private fun dispatchDelta(root: JSONObject, params: JSONObject, delta: String) {
        val id = correlationId(root, params)
        if (id != null) {
            streamHandlers[id]?.invoke(BridgeStreamEvent.Delta(delta))
            return
        }
        if (streamHandlers.size == 1) {
            streamHandlers.values.first().invoke(BridgeStreamEvent.Delta(delta))
        }
    }

    private fun dispatchDone(root: JSONObject, params: JSONObject) {
        val id = correlationId(root, params)
        if (id != null) {
            streamHandlers.remove(id)?.invoke(BridgeStreamEvent.Done)
            rpcWaiters.remove(id)
            return
        }
        if (streamHandlers.size == 1) {
            val key = streamHandlers.keys.first()
            streamHandlers.remove(key)?.invoke(BridgeStreamEvent.Done)
            rpcWaiters.remove(key)
        }
    }

    private fun dispatchError(root: JSONObject, params: JSONObject, message: String) {
        val id = correlationId(root, params)
        if (id != null) {
            streamHandlers.remove(id)?.invoke(BridgeStreamEvent.Error(message))
            return
        }
        streamHandlers.keys.toList().forEach { key ->
            streamHandlers.remove(key)?.invoke(BridgeStreamEvent.Error(message))
        }
    }

    private fun correlationId(root: JSONObject, params: JSONObject): String? {
        val candidates = listOf(
            root.opt("id")?.toString(),
            params.optString("client_request_id"),
            params.optString("request_id"),
            params.opt("id")?.toString(),
        )
        return candidates.firstOrNull { !it.isNullOrBlank() && streamHandlers.containsKey(it) }
    }

    private fun extractText(obj: JSONObject): String? {
        val direct = sequenceOf("text", "content", "delta", "token")
            .map { obj.optString(it) }
            .firstOrNull { it.isNotEmpty() }
        if (direct != null) return direct
        val payload = obj.optJSONObject("payload") ?: return null
        return sequenceOf("text", "content", "delta")
            .map { payload.optString(it) }
            .firstOrNull { it.isNotEmpty() }
    }

    private fun failAll(error: Throwable) {
        val message = com.eraherm.hermchat.util.UserFacingError.of(error, "连接断开")
        streamHandlers.keys.toList().forEach { key ->
            streamHandlers.remove(key)?.invoke(BridgeStreamEvent.Error(message))
        }
        rpcWaiters.keys.toList().forEach { key ->
            rpcWaiters.remove(key)?.invoke(Result.failure(error))
        }
    }

    private sealed interface BridgeStreamEvent {
        data class Delta(val text: String) : BridgeStreamEvent
        data object Done : BridgeStreamEvent
        data class Error(val message: String) : BridgeStreamEvent
    }

    companion object {
        fun detectProtocol(endpoint: String): WsProtocol {
            val path = endpoint.substringAfter("://", endpoint).lowercase()
            return when {
                path.contains("/api/ws") -> WsProtocol.JSON_RPC
                path.contains("/v1/ws") -> WsProtocol.AGENT_MESSAGE
                else -> WsProtocol.SIMPLE
            }
        }

        fun forHermes(endpoint: String): HermesBridgeClient {
            val preferred = when {
                endpoint.contains("/v1/ws") -> WsProtocol.AGENT_MESSAGE
                endpoint.contains("/api/ws") -> WsProtocol.JSON_RPC
                else -> WsProtocol.JSON_RPC
            }
            return HermesBridgeClient(endpoint, preferred)
        }

        private fun defaultClient(): OkHttpClient = SharedHttpClients.websocket(pingSeconds = 30)
    }
}
