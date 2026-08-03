package com.eraherm.hermchat.data.local

import android.content.Context
import com.eraherm.hermchat.data.network.TransferProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** On-device LLM model files（内置目录 + 用户从开源搜索追加）。 */
class LocalModelStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "local_llm")
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _custom = MutableStateFlow(loadCustom())
    val customCatalog: StateFlow<Map<String, ModelEntry>> = _custom.asStateFlow()

    private val stopFlags = ConcurrentHashMap<String, AtomicBoolean>()
    @Volatile
    private var activeConnection: HttpURLConnection? = null
    @Volatile
    private var activeModelId: String? = null

    fun storageDir(): File = root

    fun allCatalog(): Map<String, ModelEntry> = BUILTIN + _custom.value

    fun modelFile(modelId: String = DEFAULT_MODEL_ID): File =
        File(root, allCatalog()[modelId]?.fileName ?: DEFAULT_FILE)

    fun partFile(modelId: String = DEFAULT_MODEL_ID): File {
        val dest = modelFile(modelId)
        return File(dest.parentFile, "${dest.name}.part")
    }

    fun isReady(modelId: String = DEFAULT_MODEL_ID): Boolean {
        val entry = allCatalog()[modelId] ?: return false
        val file = modelFile(modelId)
        return file.exists() && file.length() >= entry.minBytes
    }

    fun hasPartial(modelId: String = DEFAULT_MODEL_ID): Boolean {
        if (isReady(modelId)) return false
        val part = partFile(modelId)
        return part.exists() && part.length() > 0L
    }

    fun expectedBytes(modelId: String = DEFAULT_MODEL_ID): Long =
        allCatalog()[modelId]?.approxBytes ?: 0L

    fun isKnown(modelId: String): Boolean = allCatalog().containsKey(modelId)

    fun listStatuses(): List<ModelStatus> {
        return allCatalog().values.map { entry ->
            val file = File(root, entry.fileName)
            val part = File(root, "${entry.fileName}.part")
            val ready = file.exists() && file.length() >= entry.minBytes
            val partialBytes = when {
                ready -> 0L
                part.exists() -> part.length()
                else -> 0L
            }
            ModelStatus(
                entry = entry,
                installed = ready,
                bytesOnDisk = if (ready) file.length() else partialBytes,
                partial = !ready && partialBytes > 0L,
            )
        }.sortedWith(
            compareBy<ModelStatus> { it.entry.requiresHfToken }
                .thenByDescending { it.installed }
                .thenByDescending { it.partial }
                .thenBy { it.entry.label },
        )
    }

    fun register(entry: ModelEntry) {
        val next = _custom.value.toMutableMap()
        next[entry.id] = entry
        persistCustom(next)
        _custom.value = next
    }

    /** 暂停当前（或指定）下载：断开连接并保留 `.part`，可再点继续。 */
    fun pauseDownload(modelId: String? = null) {
        val id = modelId ?: activeModelId
        if (id != null) {
            stopFlags[id]?.set(true)
        }
        runCatching { activeConnection?.disconnect() }
    }

    fun ensureInstalled(
        modelId: String = DEFAULT_MODEL_ID,
        hfToken: String = "",
        isActive: () -> Boolean = { true },
        onProgress: (TransferProgress) -> Unit = {},
    ): Result<File> = runCatching {
        if (isReady(modelId)) return@runCatching modelFile(modelId)
        val entry = allCatalog()[modelId] ?: error("未知模型")
        root.mkdirs()
        val dest = modelFile(modelId)
        val tmp = partFile(modelId)
        val stop = AtomicBoolean(false)
        stopFlags[modelId] = stop
        activeModelId = modelId
        try {
            downloadWithRedirects(
                startUrl = entry.url,
                hfToken = hfToken.trim(),
                label = entry.label,
                approxBytes = entry.approxBytes,
                dest = tmp,
                shouldContinue = { isActive() && !stop.get() },
                onProgress = onProgress,
            )
            if (!isActive() || stop.get()) {
                error(PAUSED_MESSAGE)
            }
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
        } finally {
            stopFlags.remove(modelId, stop)
            if (activeModelId == modelId) activeModelId = null
        }
    }

    fun delete(modelId: String = DEFAULT_MODEL_ID) {
        pauseDownload(modelId)
        val file = modelFile(modelId)
        file.delete()
        partFile(modelId).delete()
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
        shouldContinue: () -> Boolean,
        onProgress: (TransferProgress) -> Unit,
    ) {
        var current = startUrl
        repeat(8) {
            if (!shouldContinue()) error(PAUSED_MESSAGE)
            var existing = if (dest.exists()) dest.length() else 0L
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 600_000
                if (hfToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $hfToken")
                }
                if (existing > 0L) {
                    setRequestProperty("Range", "bytes=$existing-")
                }
            }
            activeConnection = connection
            val code = try {
                connection.responseCode
            } catch (e: Exception) {
                activeConnection = null
                connection.disconnect()
                if (!shouldContinue()) throw IllegalStateException(PAUSED_MESSAGE)
                throw e
            }
            when (code) {
                in 300..399 -> {
                    val next = connection.getHeaderField("Location")
                        ?: error("重定向失败")
                    current = if (next.startsWith("http")) {
                        next
                    } else {
                        URL(URL(current), next).toString()
                    }
                    activeConnection = null
                    connection.disconnect()
                }
                in 200..299 -> {
                    val resumed = code == HttpURLConnection.HTTP_PARTIAL && existing > 0L
                    if (existing > 0L && !resumed) {
                        // 服务端忽略 Range，整文件重下
                        dest.delete()
                        existing = 0L
                    }
                    val contentLen = connection.contentLengthLong
                    val rangeTotal = parseContentRangeTotal(
                        connection.getHeaderField("Content-Range"),
                    )
                    val total = when {
                        rangeTotal != null && rangeTotal > 0L -> rangeTotal
                        resumed && contentLen > 0L -> existing + contentLen
                        contentLen > 0L -> contentLen
                        else -> approxBytes
                    }
                    val started = System.nanoTime()
                    var written = existing
                    try {
                        connection.inputStream.use { input ->
                            FileOutputStream(dest, resumed).use { output ->
                                val buf = ByteArray(BUFFER_BYTES)
                                var read: Int
                                var lastEmit = 0L
                                while (input.read(buf).also { read = it } >= 0) {
                                    if (!shouldContinue()) {
                                        error(PAUSED_MESSAGE)
                                    }
                                    output.write(buf, 0, read)
                                    written += read
                                    val now = System.nanoTime()
                                    if (now - lastEmit >= 200_000_000L || written >= total) {
                                        lastEmit = now
                                        val sessionBytes = (written - existing).coerceAtLeast(0L)
                                        val elapsedSec =
                                            ((now - started) / 1_000_000_000.0).coerceAtLeast(0.001)
                                        onProgress(
                                            TransferProgress(
                                                label = label,
                                                bytesRead = written,
                                                totalBytes = total,
                                                bytesPerSec = (sessionBytes / elapsedSec).toLong(),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (!shouldContinue()) error(PAUSED_MESSAGE)
                        throw e
                    } finally {
                        activeConnection = null
                        connection.disconnect()
                    }
                    onProgress(
                        TransferProgress(
                            label = label,
                            bytesRead = dest.length(),
                            totalBytes = total.coerceAtLeast(dest.length()),
                            bytesPerSec = 0L,
                        ),
                    )
                    if (!shouldContinue()) error(PAUSED_MESSAGE)
                    return
                }
                401, 403 -> {
                    activeConnection = null
                    connection.disconnect()
                    error(hfAuthError(code, hfToken))
                }
                else -> {
                    activeConnection = null
                    connection.disconnect()
                    error("下载失败 HTTP $code")
                }
            }
        }
        error("重定向过多")
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val match = CONTENT_RANGE.find(header) ?: return null
        val total = match.groupValues[3]
        if (total == "*") return null
        return total.toLongOrNull()
    }

    private fun hfAuthError(code: Int, hfToken: String): String {
        return if (hfToken.isBlank()) {
            "该模型需 Hugging Face 令牌：上方填写后重试，并在网页接受许可；或改下「免令牌」目录中的 Qwen / TinyLlama"
        } else {
            "令牌无效或未接受模型许可（HTTP $code）"
        }
    }

    data class ModelEntry(
        val id: String,
        val label: String,
        val fileName: String,
        val url: String,
        val minBytes: Long,
        val approxBytes: Long,
        val source: String = "builtin",
        /** Gemma 等门控权重为 true；默认 Qwen/TinyLlama 为 false，可直下。 */
        val requiresHfToken: Boolean = false,
    )

    data class ModelStatus(
        val entry: ModelEntry,
        val installed: Boolean,
        val bytesOnDisk: Long,
        val partial: Boolean = false,
    )

    companion object {
        /** 默认：公开可下的 Qwen2.5 0.5B（无需 HF 令牌）。 */
        const val DEFAULT_MODEL_ID = "qwen25-05b-it-q8"
        const val MODEL_TINYLLAMA_ID = "tinyllama-11b-chat-q8"
        const val MODEL_GEMMA_270M_ID = "gemma3-270m-it-q8"
        const val MODEL_1B_ID = "gemma3-1b-it-int4"
        /** @deprecated 旧默认 id，仍保留在目录供已下载用户 */
        @Deprecated("Use DEFAULT_MODEL_ID", ReplaceWith("DEFAULT_MODEL_ID"))
        const val LEGACY_DEFAULT_MODEL_ID = MODEL_GEMMA_270M_ID

        const val PAUSED_MESSAGE = "已暂停，可继续下载"

        private const val PREFS = "hermchat_models"
        private const val KEY_CUSTOM = "custom_catalog_json"
        private const val KEY_HF_TOKEN = "hf_token"
        private const val DEFAULT_FILE =
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
        private const val MIN_BYTES_QWEN_05 = 400L * 1024L * 1024L
        private const val MIN_BYTES_TINY = 800L * 1024L * 1024L
        private const val MIN_BYTES_LIGHT = 100L * 1024L * 1024L
        private const val MIN_BYTES_1B = 50L * 1024L * 1024L
        private const val BUFFER_BYTES = 64 * 1024
        private val CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)

        fun isKnownModelId(id: String): Boolean = BUILTIN.containsKey(id)

        val BUILTIN: Map<String, ModelEntry> = linkedMapOf(
            DEFAULT_MODEL_ID to ModelEntry(
                id = DEFAULT_MODEL_ID,
                label = "Qwen2.5 0.5B（免令牌）",
                fileName = DEFAULT_FILE,
                url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
                    "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                minBytes = MIN_BYTES_QWEN_05,
                approxBytes = 547L * 1024L * 1024L,
                source = "builtin",
                requiresHfToken = false,
            ),
            MODEL_TINYLLAMA_ID to ModelEntry(
                id = MODEL_TINYLLAMA_ID,
                label = "TinyLlama 1.1B（免令牌）",
                fileName = "TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
                url = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/" +
                    "TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
                minBytes = MIN_BYTES_TINY,
                approxBytes = 1148L * 1024L * 1024L,
                source = "builtin",
                requiresHfToken = false,
            ),
            MODEL_GEMMA_270M_ID to ModelEntry(
                id = MODEL_GEMMA_270M_ID,
                label = "Gemma 3 270M（需令牌）",
                fileName = "gemma3-270m-it-q8.task",
                url = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task",
                minBytes = MIN_BYTES_LIGHT,
                approxBytes = 318L * 1024L * 1024L,
                source = "builtin",
                requiresHfToken = true,
            ),
            MODEL_1B_ID to ModelEntry(
                id = MODEL_1B_ID,
                label = "Gemma 3 1B（需令牌）",
                fileName = "gemma3-1b-it-int4.task",
                url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                minBytes = MIN_BYTES_1B,
                approxBytes = 550L * 1024L * 1024L,
                source = "builtin",
                requiresHfToken = true,
            ),
        )

        /** @deprecated 用实例 [isKnown]；保留给旧调用只认内置 */
        @JvmField
        val catalog: Map<String, ModelEntry> = BUILTIN
    }
}
