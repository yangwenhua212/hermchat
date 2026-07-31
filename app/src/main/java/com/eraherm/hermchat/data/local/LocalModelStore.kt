package com.eraherm.hermchat.data.local

import android.content.Context
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
        val minBytes = catalog[modelId]?.minBytes ?: MIN_BYTES
        return file.exists() && file.length() >= minBytes
    }

    fun ensureInstalled(
        modelId: String = DEFAULT_MODEL_ID,
        hfToken: String = "",
        onProgress: (Float) -> Unit = {},
    ): Result<File> = runCatching {
        if (isReady(modelId)) return@runCatching modelFile(modelId)
        val entry = catalog[modelId] ?: error("未知模型")
        root.mkdirs()
        val dest = modelFile(modelId)
        val tmp = File(dest.parentFile, "${dest.name}.part")
        tmp.delete()
        downloadWithRedirects(entry.url, hfToken.trim(), tmp, onProgress)
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
        dest: File,
        onProgress: (Float) -> Unit,
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
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
                    connection.inputStream.use { input ->
                        dest.outputStream().use { output ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read: Int
                            var written = 0L
                            while (input.read(buf).also { read = it } >= 0) {
                                output.write(buf, 0, read)
                                written += read
                                if (total > 0) {
                                    onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                    connection.disconnect()
                    onProgress(1f)
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
    )

    companion object {
        const val DEFAULT_MODEL_ID = "gemma3-1b-it-int4"
        private const val DEFAULT_FILE = "gemma3-1b-it-int4.task"
        private const val MIN_BYTES = 50L * 1024L * 1024L

        val catalog: Map<String, ModelEntry> = mapOf(
            DEFAULT_MODEL_ID to ModelEntry(
                id = DEFAULT_MODEL_ID,
                label = "Gemma 3 1B",
                fileName = DEFAULT_FILE,
                url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                minBytes = MIN_BYTES,
            ),
        )
    }
}
