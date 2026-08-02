package com.eraherm.hermchat.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ConnectionTester(
    private val client: OkHttpClient = sharedClient,
) {
    suspend fun test(
        endpoint: String,
        apiKey: String = "",
        model: String = "",
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = endpoint.trim()
        if (url.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("地址不能为空"))
        }
        runCatching {
            when {
                url.startsWith("ws://", ignoreCase = true) ||
                    url.startsWith("wss://", ignoreCase = true) -> testWebSocket(url)

                url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true) ->
                    testHttpCompat(url, sanitizeKey(apiKey), model.trim())

                else -> error("请使用 ws://、wss://、http:// 或 https:// 开头的地址")
            }
        }
    }

    private fun testHttpCompat(rawUrl: String, apiKey: String, model: String): String {
        val root = rawUrl.trimEnd('/')
        val chatUrl = when {
            root.endsWith("/v1/chat/completions", ignoreCase = true) -> root
            root.endsWith("/v1", ignoreCase = true) -> "$root/chat/completions"
            else -> "$root/v1/chat/completions"
        }
        val modelsUrl = when {
            root.endsWith("/v1/chat/completions", ignoreCase = true) ->
                root.removeSuffix("/chat/completions") + "/models"
            root.endsWith("/v1", ignoreCase = true) -> "$root/models"
            else -> "$root/v1/models"
        }

        // 1) Real OpenAI-compatible ping (what chat will use).
        val chatResult = runCatching { postChatPing(chatUrl, apiKey, model) }
        chatResult.onSuccess { return it }

        // 2) Fallback: /v1/models proves host + auth path.
        val modelsResult = runCatching { getModels(modelsUrl, apiKey) }
        modelsResult.onSuccess { return it }

        // Prefer the more specific chat error if both failed after reaching the host.
        val chatErr = chatResult.exceptionOrNull()
        val modelsErr = modelsResult.exceptionOrNull()
        if (chatErr is HttpProbeException) throw chatErr
        if (modelsErr is HttpProbeException) throw modelsErr
        throw chatErr ?: modelsErr ?: IllegalStateException("测连失败")
    }

    private fun postChatPing(url: String, apiKey: String, model: String): String {
        val body = JSONObject()
            .put("model", model.ifBlank { "default" })
            .put("stream", false)
            .put("max_tokens", 1)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", "ping"),
                ),
            )
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        client.newCall(builder.build()).execute().use { response ->
            return interpretHttp(response, kind = "chat")
        }
    }

    private fun getModels(url: String, apiKey: String): String {
        val builder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        client.newCall(builder.build()).execute().use { response ->
            return interpretHttp(response, kind = "models")
        }
    }

    private fun interpretHttp(response: Response, kind: String): String {
        val code = response.code
        val errBody = response.body?.string().orEmpty().take(160)
        when (code) {
            in 200..299 -> return if (kind == "chat") "HTTP 测连成功" else "服务可达"
            401, 403 -> error("服务可达，API Key 无效")
            404 -> throw HttpProbeException("路径不存在，请检查地址是否含正确端口")
            422, 400 -> {
                if (errBody.contains("model", ignoreCase = true)) {
                    return "服务可达，请检查模型名"
                }
                throw HttpProbeException("请求被拒绝($code)")
            }
            else -> throw HttpProbeException("HTTP $code")
        }
    }

    private suspend fun testWebSocket(url: String): String = withTimeout(5_000) {
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(url).build()
            val webSocket = client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.close(1000, "probe")
                        if (cont.isActive) {
                            cont.resume("WebSocket 已接通")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (cont.isActive) {
                            cont.resumeWith(Result.failure(t))
                        }
                    }
                },
            )
            cont.invokeOnCancellation {
                webSocket.cancel()
            }
        }
    }

    private class HttpProbeException(message: String) : IllegalStateException(message)

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()

        fun sanitizeKey(raw: String): String =
            raw.trim().trimStart(')', '(', ' ', '\u00A0')
    }
}
