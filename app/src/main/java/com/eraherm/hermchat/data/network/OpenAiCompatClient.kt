package com.eraherm.hermchat.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * OpenAI-compatible chat completions (SSE) for HTTP Agents.
 *
 * Hermes HTTP API：不带 [HEADER_SESSION] 时会用「首条用户消息 hash」隐式绑死会话，
 * 上下文只增不减。此处每次对话持有独立 UUID，新建对话时 [resetConversation] 换新。
 */
class OpenAiCompatClient(
    baseUrl: String,
    private val apiKey: String = "",
    private val model: String = "default",
    private val client: OkHttpClient = defaultClient(),
) : StreamingChatClient {

    private val root = baseUrl.trim().trimEnd('/')
    private val sessionId = AtomicReference(newSessionId())
    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    override suspend fun ensureConnected() {
        // Connectivity is proven on the first successful stream response.
    }

    override fun resetConversation() {
        sessionId.set(newSessionId())
    }

    override fun streamChat(prompt: String): Flow<String> = flow {
        val url = when {
            root.endsWith("/v1/chat/completions", ignoreCase = true) -> root
            root.endsWith("/v1", ignoreCase = true) -> "$root/chat/completions"
            else -> "$root/v1/chat/completions"
        }
        // 仅发本轮 user：Hermes 用 Session-Id 在服务端延续上下文。
        // 勿把本地全量历史塞进 body，否则会与服务端会话双重堆叠。
        val bodyJson = JSONObject()
            .put("model", model.ifBlank { "default" })
            .put("stream", true)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt),
                ),
            )
        val requestBuilder = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "text/event-stream")
            .header(HEADER_SESSION, sessionId.get())
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${ConnectionTester.sanitizeKey(apiKey)}")
        }
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty().take(240)
                error("HTTP ${response.code}: ${response.message}${if (errBody.isBlank()) "" else " · $errBody"}")
            }
            _connected.value = true
            val source = response.body?.source() ?: error("空响应")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                if (data.isEmpty()) continue
                val piece = parseSseData(data) ?: continue
                if (piece.isNotEmpty()) emit(piece)
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        _connected.value = false
    }

    private fun parseSseData(data: String): String? {
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return data
        val choices = json.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val choice = choices.getJSONObject(0)
        val delta = choice.optJSONObject("delta")
        if (delta != null) {
            return delta.optString("content").takeIf { it.isNotEmpty() }
        }
        val message = choice.optJSONObject("message")
        return message?.optString("content")?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val HEADER_SESSION = "X-Hermes-Session-Id"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun newSessionId(): String = UUID.randomUUID().toString()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
