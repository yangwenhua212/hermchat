package com.eraherm.hermchat.data.local

import android.content.Context
import com.eraherm.hermchat.data.network.TransferProgress
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** On-device LLM model files for Phase B local runtime. */
class LocalModelStore(
    context: Context,
) {
    private val root = File(context.filesDir, "local_llm")

    fun modelFile(modelId: String = DEFAULT_MODEL_ID): File =
        File(root, catalog[modelId]?.fileName ?: DEFAULT_FILE)

    fun isReady(modelId: String = DEFAULT_MODEL_ID): Boolean {
        val file = modelFile(modelId)
        val minBytes = catalog[modelId]?.minBytes ?: MIN_BYTES_LIGHT
        return file.exists() && file.length() >= minBytes
    }

    fun expectedBytes(modelId: String = DEFAULT_MODEL_ID): Long =
        catalog[modelId]?.approxBytes ?: 0L

    fun ensureInstalled(
        modelId: String = DEFAULT_MODEL_ID,
        hfToken: String = "",
        onProgress: (TransferProgress) -> Unit = {},
    ): Result<File> = runCatching {
        if (isReady(modelId)) return@runCatching modelFile(modelId)
        val entry = catalog[modelId] ?: error("未知模型")
        root.mkdirs()
        val dest = modelFile(modelId)
        val tmp = File(dest.parentFile, "${dest.name}.part")
        tmp.delete()
        downloadWithRedirects(entry.url, hfToken.trim(), entry.label, entry.approxBytes, tmp, onProgress)
        if (tmp.length() < entry.minBytes) {
            tmp.delete()
            error("模型文件不完整")
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        dest
    }

    fun delete(modelId: String = DEFAULT_MODEL_ID) {
        modelFile(modelId).delete()
        File(modelFile(modelId).parentFile, "${modelFile(modelId).name}.part").delete()
    }

    private fun downloadWithRedirects(
        startUrl: String,
        hfToken: String,
        label: String,
        approxBytes: Long,
        dest: File,
        onProgress: (TransferProgress) -> Unit,
    ) {
        var current = startUrl
        repeat(8) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 600_000
                if (hfToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $hfToken")
                }
            }
            val code = connection.responseCode
            when (code) {
                in 300..399 -> {
                    val next = connection.getHeaderField("Location")
                        ?: error("重定向失败")
                    current = if (next.startsWith("http")) next else {
                        URL(URL(current), next).toString()
                    }
                    connection.disconnect()
                }
                in 200..299 -> {
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: approxBytes
                    val started = System.nanoTime()
                    connection.inputStream.use { input ->
                        dest.outputStream().use { output ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read: Int
                            var written = 0L
                            var lastEmit = 0L
                            while (input.read(buf).also { read = it } >= 0) {
                                output.write(buf, 0, read)
                                written += read
                                val now = System.nanoTime()
                                if (now - lastEmit >= 200_000_000L || written == total) {
                                    lastEmit = now
                                    val elapsedSec = ((now - started) / 1_000_000_000.0).coerceAtLeast(0.001)
                                    onProgress(
                                        TransferProgress(
                                            label = label,
                                            bytesRead = written,
                                            totalBytes = total,
                                            bytesPerSec = (written / elapsedSec).toLong(),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    connection.disconnect()
                    onProgress(
                        TransferProgress(
                            label = label,
                            bytesRead = dest.length(),
                            totalBytes = total.coerceAtLeast(dest.length()),
                            bytesPerSec = 0L,
                        ),
                    )
                    return
                }
                else -> {
                    connection.disconnect()
                    error("下载失败 HTTP $code")
                }
            }
        }
        error("重定向过多")
    }

    data class ModelEntry(
        val id: String,
        val label: String,
        val fileName: String,
        val url: String,
        val minBytes: Long,
        val approxBytes: Long,
    )

    companion object {
        /** 默认：Gemma 3 270M Q8，体积与内存占用最小。 */
        const val DEFAULT_MODEL_ID = "gemma3-270m-it-q8"
        const val MODEL_1B_ID = "gemma3-1b-it-int4"

        private const val DEFAULT_FILE = "gemma3-270m-it-q8.task"
        private const val MIN_BYTES_LIGHT = 100L * 1024L * 1024L
        private const val MIN_BYTES_1B = 50L * 1024L * 1024L

        fun isKnownModelId(id: String): Boolean = catalog.containsKey(id)

        val catalog: Map<String, ModelEntry> = mapOf(
            DEFAULT_MODEL_ID to ModelEntry(
                id = DEFAULT_MODEL_ID,
                label = "Gemma 3 270M",
                fileName = DEFAULT_FILE,
                url = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task",
                minBytes = MIN_BYTES_LIGHT,
                approxBytes = 318L * 1024L * 1024L,
            ),
            MODEL_1B_ID to ModelEntry(
                id = MODEL_1B_ID,
                label = "Gemma 3 1B",
                fileName = "gemma3-1b-it-int4.task",
                url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                minBytes = MIN_BYTES_1B,
                approxBytes = 550L * 1024L * 1024L,
            ),
        )
    }
}
