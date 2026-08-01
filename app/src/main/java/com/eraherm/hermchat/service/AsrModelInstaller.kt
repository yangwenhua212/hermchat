package com.eraherm.hermchat.service

import android.content.Context
import com.eraherm.hermchat.data.network.TransferProgress
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Downloads small Chinese streaming ASR model for offline command dictation. */
class AsrModelInstaller(
    context: Context,
    private val http: OkHttpClient = defaultClient(),
) {
    private val root = File(context.filesDir, MODEL_DIR)

    fun isReady(): Boolean {
        if (!File(root, READY_MARKER).exists()) return false
        return REQUIRED_FILES.all { File(root, it).exists() && File(root, it).length() > 0L }
    }

    fun modelDir(): File = root

    fun ensureInstalled(onProgress: (TransferProgress) -> Unit = {}): Result<File> = runCatching {
        if (isReady()) return@runCatching root
        root.mkdirs()
        REQUIRED_FILES.forEach { name ->
            val dest = File(root, name)
            if (dest.exists() && dest.length() > 0L) return@forEach
            download(name, dest, onProgress)
        }
        File(root, READY_MARKER).writeText("ok")
        root
    }

    private fun download(
        name: String,
        dest: File,
        onProgress: (TransferProgress) -> Unit,
    ) {
        val tmp = File(dest.parentFile, "$name.part")
        tmp.delete()
        var lastError: Exception? = null
        for (base in BASE_URLS) {
            try {
                val request = Request.Builder().url("$base/$name").get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("empty body")
                    val total = body.contentLength().takeIf { it > 0 } ?: -1L
                    val started = System.nanoTime()
                    var written = 0L
                    var lastEmit = 0L
                    tmp.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read: Int
                            while (input.read(buf).also { read = it } >= 0) {
                                out.write(buf, 0, read)
                                written += read
                                val now = System.nanoTime()
                                if (now - lastEmit >= 200_000_000L) {
                                    lastEmit = now
                                    val elapsed = ((now - started) / 1_000_000_000.0).coerceAtLeast(0.001)
                                    onProgress(
                                        TransferProgress(
                                            label = name,
                                            bytesRead = written,
                                            totalBytes = total,
                                            bytesPerSec = (written / elapsed).toLong(),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    onProgress(
                        TransferProgress(
                            label = name,
                            bytesRead = written,
                            totalBytes = total.coerceAtLeast(written),
                            bytesPerSec = 0L,
                        ),
                    )
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
        const val MODEL_DIR = "asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
        private const val READY_MARKER = ".ready"

        val REQUIRED_FILES = listOf(
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
            "tokens.txt",
        )

        private val BASE_URLS = listOf(
            "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/resolve/main",
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/resolve/main",
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
