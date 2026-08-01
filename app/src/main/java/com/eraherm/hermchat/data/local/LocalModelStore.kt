package com.eraherm.hermchat.data.local

import android.content.Context
import com.eraherm.hermchat.data.network.TransferProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** On-device LLM model files（内置目录 + 用户从开源搜索追加）。 */
class LocalModelStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "local_llm")
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _custom = MutableStateFlow(loadCustom())
    val customCatalog: StateFlow<Map<String, ModelEntry>> = _custom.asStateFlow()

    fun storageDir(): File = root

    fun allCatalog(): Map<String, ModelEntry> = BUILTIN + _custom.value

    fun modelFile(modelId: String = DEFAULT_MODEL_ID): File =
        File(root, allCatalog()[modelId]?.fileName ?: DEFAULT_FILE)

    fun isReady(modelId: String = DEFAULT_MODEL_ID): Boolean {
        val entry = allCatalog()[modelId] ?: return false
        val file = modelFile(modelId)
        return file.exists() && file.length() >= entry.minBytes
    }

    fun expectedBytes(modelId: String = DEFAULT_MODEL_ID): Long =
        allCatalog()[modelId]?.approxBytes ?: 0L

    fun isKnown(modelId: String): Boolean = allCatalog().containsKey(modelId)

    fun listStatuses(): List<ModelStatus> {
        return allCatalog().values.map { entry ->
            val file = File(root, entry.fileName)
            val ready = file.exists() && file.length() >= entry.minBytes
            ModelStatus(
                entry = entry,
                installed = ready,
                bytesOnDisk = if (file.exists()) file.length() else 0L,
            )
        }.sortedWith(
            compareByDescending<ModelStatus> { it.installed }
                .thenBy { it.entry.label },
        )
    }

    fun register(entry: ModelEntry) {
        val next = _custom.value.toMutableMap()
        next[entry.id] = entry
        persistCustom(next)
        _custom.value = next
    }

    fun ensureInstalled(
        modelId: String = DEFAULT_MODEL_ID,
        hfToken: String = "",
        onProgress: (TransferProgress) -> Unit = {},
    ): Result<File> = runCatching {
        if (isReady(modelId)) return@runCatching modelFile(modelId)
        val entry = allCatalog()[modelId] ?: error("未知模型")
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
        val file = modelFile(modelId)
        file.delete()
        File(file.parentFile, "${file.name}.part").delete()
    }

    fun uninstallAndForget(modelId: String) {
        delete(modelId)
        if (!BUILTIN.containsKey(modelId)) {
            val next = _custom.value.toMutableMap()
            next.remove(modelId)
            persistCustom(next)
            _custom.value = next
        }
    }

    fun hfToken(): String = prefs.getString(KEY_HF_TOKEN, "").orEmpty()

    fun setHfToken(token: String) {
        prefs.edit().putString(KEY_HF_TOKEN, token.trim()).apply()
    }

    private fun loadCustom(): Map<String, ModelEntry> {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    put(
                        id,
                        ModelEntry(
                            id = id,
                            label = obj.getString("label"),
                            fileName = obj.getString("fileName"),
                            url = obj.getString("url"),
                            minBytes = obj.optLong("minBytes", MIN_BYTES_LIGHT),
                            approxBytes = obj.optLong("approxBytes", 0L),
                            source = obj.optString("source", "custom"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun persistCustom(map: Map<String, ModelEntry>) {
        val array = JSONArray()
        map.values.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("label", e.label)
                    .put("fileName", e.fileName)
                    .put("url", e.url)
                    .put("minBytes", e.minBytes)
                    .put("approxBytes", e.approxBytes)
                    .put("source", e.source),
            )
        }
        prefs.edit().putString(KEY_CUSTOM, array.toString()).apply()
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
        val source: String = "builtin",
    )

    data class ModelStatus(
        val entry: ModelEntry,
        val installed: Boolean,
        val bytesOnDisk: Long,
    )

    companion object {
        const val DEFAULT_MODEL_ID = "gemma3-270m-it-q8"
        const val MODEL_1B_ID = "gemma3-1b-it-int4"

        private const val PREFS = "hermchat_models"
        private const val KEY_CUSTOM = "custom_catalog_json"
        private const val KEY_HF_TOKEN = "hf_token"
        private const val DEFAULT_FILE = "gemma3-270m-it-q8.task"
        private const val MIN_BYTES_LIGHT = 100L * 1024L * 1024L
        private const val MIN_BYTES_1B = 50L * 1024L * 1024L

        fun isKnownModelId(id: String): Boolean = BUILTIN.containsKey(id)

        val BUILTIN: Map<String, ModelEntry> = mapOf(
            DEFAULT_MODEL_ID to ModelEntry(
                id = DEFAULT_MODEL_ID,
                label = "Gemma 3 270M",
                fileName = DEFAULT_FILE,
                url = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task",
                minBytes = MIN_BYTES_LIGHT,
                approxBytes = 318L * 1024L * 1024L,
                source = "builtin",
            ),
            MODEL_1B_ID to ModelEntry(
                id = MODEL_1B_ID,
                label = "Gemma 3 1B",
                fileName = "gemma3-1b-it-int4.task",
                url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                minBytes = MIN_BYTES_1B,
                approxBytes = 550L * 1024L * 1024L,
                source = "builtin",
            ),
        )

        /** @deprecated 用实例 [isKnown]；保留给旧调用只认内置 */
        @JvmField
        val catalog: Map<String, ModelEntry> = BUILTIN
    }
}
