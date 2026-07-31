package com.eraherm.hermchat.data.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ConnectionTester(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun test(endpoint: String): Result<String> {
        val url = endpoint.trim()
        if (url.isEmpty()) {
            return Result.failure(IllegalArgumentException("地址不能为空"))
        }
        return runCatching {
            when {
                url.startsWith("ws://", ignoreCase = true) ||
                    url.startsWith("wss://", ignoreCase = true) -> testWebSocket(url)

                url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true) -> testHttp(url)

                else -> error("请使用 ws://、wss://、http:// 或 https:// 开头的地址")
            }
        }
    }

    private fun testHttp(url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            // Any TCP/HTTP response means the host is reachable for setup purposes.
            return "HTTP ${response.code} · 可达"
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
}
