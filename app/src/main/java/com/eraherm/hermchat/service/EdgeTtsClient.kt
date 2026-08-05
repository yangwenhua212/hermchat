package com.eraherm.hermchat.service

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import com.eraherm.hermchat.data.network.SharedHttpClients
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor

/**
 * 微软 Edge 朗读（与 Hermes `tts.provider: edge` / Python edge-tts 同协议）。
 * 免费、无需 Key；默认音色 [DEFAULT_VOICE]（小艺）。
 */
class EdgeTtsClient(
    private val client: OkHttpClient = defaultClient(),
) {
    fun synthesizeToFile(
        text: String,
        dest: File,
        voice: String = DEFAULT_VOICE,
    ): Result<File> = runCatching {
        val cleaned = TtsSpeaker.prepare(text)
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), " ")
            .trim()
        require(cleaned.isNotBlank()) { "文字内容为空" }
        val voiceName = voiceNameSafe(voice.trim().ifBlank { DEFAULT_VOICE })
        val audio = synthesizeBytes(cleaned.take(MAX_CHARS), voiceName)
        require(audio.size > 64) { "音频过短" }
        dest.parentFile?.mkdirs()
        dest.writeBytes(audio)
        dest
    }

    private fun synthesizeBytes(text: String, voice: String): ByteArray {
        val escaped = xmlEscape(text)
        val ssml =
            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>" +
                "<voice name='$voice'>" +
                "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>$escaped</prosody>" +
                "</voice></speak>"

        val latch = CountDownLatch(1)
        val error = AtomicReference<Throwable?>(null)
        val audio = ByteArrayOutputStream()
        var gotAudio = false

        val url =
            "$WSS_URL&ConnectionId=${uuidNoDash()}" +
                "&Sec-MS-GEC=${generateSecMsGec()}" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", ORIGIN)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Cookie", "muid=${uuidNoDash().uppercase(Locale.US)};")
            .build()

        val ws = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val ts = jsDateString()
                    webSocket.send(
                        "X-Timestamp:$ts\r\n" +
                            "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            """{"context":{"synthesis":{"audio":{"metadataoptions":{""" +
                            """"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"true"},""" +
                            """"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}""" +
                            "\r\n",
                    )
                    val reqId = uuidNoDash()
                    webSocket.send(
                        "X-RequestId:$reqId\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:${ts}Z\r\n" +
                            "Path:ssml\r\n\r\n" +
                            ssml,
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val sep = text.indexOf("\r\n\r\n")
                    if (sep < 0) return
                    val headers = text.substring(0, sep)
                    if (headers.contains("Path:turn.end")) {
                        webSocket.close(1000, null)
                        latch.countDown()
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val data = bytes.toByteArray()
                    if (data.size < 2) return
                    // 与 edge-tts：前 2 字节为大端 header 区长度，音频在 headerLength+2 之后
                    val headerLength =
                        ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
                    if (headerLength + 2 > data.size) return
                    val header = data.copyOfRange(0, headerLength).toString(Charsets.UTF_8)
                    if (!header.contains("Path:audio")) return
                    val payload = data.copyOfRange(headerLength + 2, data.size)
                    if (payload.isEmpty()) return
                    synchronized(audio) {
                        audio.write(payload)
                        gotAudio = true
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    error.compareAndSet(null, t)
                    latch.countDown()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    latch.countDown()
                }
            },
        )

        val ok = latch.await(60, TimeUnit.SECONDS)
        if (!ok) {
            ws.cancel()
            error("Edge 朗读超时")
        }
        error.get()?.let { throw it }
        if (!gotAudio) error("Edge 朗读未返回音频，请检查音色名或网络")
        return audio.toByteArray()
    }

    private fun voiceNameSafe(voice: String): String =
        voice.filter { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    companion object {
        const val DEFAULT_VOICE = "zh-CN-XiaoyiNeural"
        private const val MAX_CHARS = 2000
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val CHROMIUM_FULL = "143.0.3650.75"
        private const val CHROMIUM_MAJOR = "143"
        private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL"
        private const val WSS_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN"
        private const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$CHROMIUM_MAJOR.0.0.0 Safari/537.36 " +
                "Edg/$CHROMIUM_MAJOR.0.0.0"
        private const val WIN_EPOCH = 11644473600L

        private fun generateSecMsGec(): String {
            var ticks = System.currentTimeMillis() / 1000.0 + WIN_EPOCH
            ticks -= ticks % 300
            val winTicks = floor(ticks * 10_000_000).toLong()
            val toHash = "$winTicks$TRUSTED_CLIENT_TOKEN"
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(toHash.toByteArray(Charsets.US_ASCII))
            return digest.joinToString("") { "%02X".format(it) }
        }

        private fun uuidNoDash(): String = UUID.randomUUID().toString().replace("-", "")

        private fun jsDateString(): String {
            val fmt = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return "${fmt.format(Date())} GMT+0000 (Coordinated Universal Time)"
        }

        private fun defaultClient(): OkHttpClient = SharedHttpClients.api.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
