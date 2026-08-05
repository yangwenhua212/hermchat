package com.eraherm.hermchat.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 进程级共享 OkHttp：共用连接池与 Dispatcher，各调用方用 [OkHttpClient.newBuilder] 覆盖超时。
 */
object SharedHttpClients {
    /** 常规 HTTP（搜索、测连偏长读、一般 API）。 */
    val api: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 局域网快速探测。 */
    val probe: OkHttpClient = api.newBuilder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    /** 大文件下载（ASR/KWS/壁纸等）。 */
    val download: OkHttpClient = api.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** OpenAI 兼容 SSE 等无限读超时。 */
    fun streamingApi(): OkHttpClient = api.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /** WebSocket（Bridge 等）。 */
    fun websocket(pingSeconds: Long = 30): OkHttpClient = api.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .pingInterval(pingSeconds, TimeUnit.SECONDS)
        .build()

    /** 测连（略短于 api）。 */
    fun connectionTest(): OkHttpClient = api.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
}
