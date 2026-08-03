package com.eraherm.hermchat.service

import com.eraherm.hermchat.data.network.ConnectionTester
import com.eraherm.hermchat.data.network.HermesEndpoint
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 `POST /v1/audio/speech`。
 * Hermes / 自建若挂了 TTS 网关可用；否则由 [ReplySpeaker] 回退系统朗读。
 */
class RemoteTtsClient(
    private val client: OkHttpClient = defaultClient(),
) {
    fun synthesizeToFile(
        endpoint: String,
        apiKey: String,
        text: String,
        dest: File,
        model: String = "tts-1",
        voice: String = "alloy",
    ): Result<File> = runCatching {
        val cleaned = TtsSpeaker.prepare(text)
        require(cleaned.isNotBlank()) { "文字内容为空" }
        val base = HermesEndpoint.normalize(endpoint).trimEnd('/')
        val url = when {
            base.endsWith("/v1/audio/speech", ignoreCase = true) -> base
            base.endsWith("/v1", ignoreCase = true) -> "$base/audio/speech"
            else -> "$base/v1/audio/speech"
        }
        val bodyJson = JSONObject()
            .put("model", model)
            .put("input", cleaned.take(4000))
            .put("voice", voice)
            .put("response_format", "mp3")
            .toString()
        val requestBuilder = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(JSON))
            .header("Content-Type", "application/json")
        val key = ConnectionTester.sanitizeKey(apiKey)
        if (key.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $key")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                // 404/405 等：助手未挂 speech 接口；文案由 ReplySpeaker 转成用户提示
                error("云端朗读不可用 ${response.code}")
            }
            val bytes = response.body?.bytes() ?: error("空音频")
            require(bytes.size > 64) { "音频过短" }
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            dest
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
