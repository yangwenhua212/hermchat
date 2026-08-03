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
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * OpenAI-compatible chat completions (SSE).
 *
 * - Hermes HTTP：靠 [HEADER_SESSION] 续上下文，body 只发本轮（+可选工具 system）
 * - DeepSeek 等 HTTP 兼容：可带本地短历史，避免无记忆
 * - [localToolsEnabled]：注入本机工具协议，模型吐 JSON → 手机执行
 */
class OpenAiCompatClient(
    baseUrl: String,
    private val apiKey: String = "",
    private val model: String = "default",
    private val localToolsEnabled: Boolean = true,
    /** true=Hermes 会话模式（少塞历史）；false=客户端带短历史（DeepSeek 等） */
    private val hermesSessionMode: Boolean = true,
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

    override fun streamChat(
        prompt: String,
        history: List<ChatTurn>,
    ): Flow<String> = flow {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            var emitted = false
            try {
                streamOnce(prompt, history) { piece ->
                    emitted = true
                    emit(piece)
                }
                return@flow
            } catch (e: Exception) {
                lastException = e
                // 已吐过字再重试会叠重复内容；只对「开流前失败」重试
                if (attempt < MAX_RETRIES - 1 && !emitted && isRetryable(e)) {
                    continue
                }
                throw e
            }
        }
        throw lastException ?: IOException("请求失败")
    }.flowOn(Dispatchers.IO)

    private suspend fun streamOnce(
        prompt: String,
        history: List<ChatTurn>,
        onPiece: suspend (String) -> Unit,
    ) {
        val url = when {
            root.endsWith("/v1/chat/completions", ignoreCase = true) -> root
            root.endsWith("/v1", ignoreCase = true) -> "$root/chat/completions"
            else -> "$root/v1/chat/completions"
        }
        val messages = JSONArray()
        if (localToolsEnabled) {
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", com.eraherm.hermchat.tools.LocalToolsPrompt.SYSTEM),
            )
        }
        if (!hermesSessionMode) {
            history.takeLast(16).forEach { turn ->
                val role = when (turn.role.lowercase()) {
                    "assistant" -> "assistant"
                    "system" -> "system"
                    else -> "user"
                }
                if (turn.content.isNotBlank()) {
                    messages.put(
                        JSONObject().put("role", role).put("content", turn.content),
                    )
                }
            }
        }
        val userContent = if (localToolsEnabled) {
            com.eraherm.hermchat.tools.LocalToolsPrompt.userPrefix() + prompt
        } else {
            prompt
        }
        messages.put(JSONObject().put("role", "user").put("content", userContent))

        val bodyJson = JSONObject()
            .put("model", model.ifBlank { "default" })
            .put("stream", true)
            .put("messages", messages)
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
                val code = response.code
                val errBody = response.body?.string().orEmpty().take(240)
                val msg = "HTTP $code: ${response.message}${if (errBody.isBlank()) "" else " · $errBody"}"
                if (code in 400..499) {
                    // 客户端错误不重试
                    error(msg)
                }
                throw IOException(msg)
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
                if (piece.isNotEmpty()) onPiece(piece)
            }
        }
    }

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

    /** 连接/超时等可重试；未知主机不重试（已吐字时由外层禁止重试）。 */
    private fun isRetryable(e: Exception): Boolean = when (e) {
        is UnknownHostException -> false
        is SocketTimeoutException -> true
        is IOException -> true
        else -> false
    }

    companion object {
        private const val MAX_RETRIES = 2
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
