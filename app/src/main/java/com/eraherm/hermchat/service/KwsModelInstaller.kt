package com.eraherm.hermchat.service

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Downloads Chinese KWS model files into app filesDir (first-run / offline wake). */
class KwsModelInstaller(
    context: Context,
    private val http: OkHttpClient = defaultClient(),
) {
    private val root = File(context.filesDir, MODEL_DIR)

    fun isReady(): Boolean {
        if (!File(root, READY_MARKER).exists()) return false
        return REQUIRED_FILES.all { File(root, it).exists() && File(root, it).length() > 0L }
    }

    fun modelDir(): File = root

    fun ensureInstalled(onProgress: (String) -> Unit = {}): Result<File> = runCatching {
        if (isReady()) return@runCatching root
        root.mkdirs()
        REQUIRED_FILES.forEach { name ->
            val dest = File(root, name)
            if (dest.exists() && dest.length() > 0L) return@forEach
            onProgress(name)
            download(name, dest)
        }
        File(root, READY_MARKER).writeText("ok")
        root
    }

    private fun download(name: String, dest: File) {
        val tmp = File(dest.parentFile, "$name.part")
        tmp.delete()
        var lastError: Exception? = null
        for (base in BASE_URLS) {
            try {
                val request = Request.Builder().url("$base/$name").get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}")
                    }
                    val body = response.body ?: error("empty body")
                    tmp.outputStream().use { out ->
                        body.byteStream().copyTo(out)
                    }
                }
                if (tmp.length() <= 0L) error("empty file")
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                return
            } catch (e: Exception) {
                lastError = e
                tmp.delete()
            }
        }
        throw lastError ?: IllegalStateException("download failed: $name")
    }

    companion object {
        const val MODEL_DIR = "kws/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01"
        private const val READY_MARKER = ".ready"

        val REQUIRED_FILES = listOf(
            "encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            "joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
            "tokens.txt",
            "keywords.txt",
        )

        private val BASE_URLS = listOf(
            "https://www.modelscope.cn/models/pkufool/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/resolve/master",
            "https://huggingface.co/csukuangfj/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/resolve/main",
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
