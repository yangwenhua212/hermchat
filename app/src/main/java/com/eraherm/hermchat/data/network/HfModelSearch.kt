package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.local.LocalModelStore
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 在 Hugging Face 上搜 litert-community 等可端侧运行的 `.task` 模型。
 */
object HfModelSearch {
    fun search(
        query: String,
        hfToken: String = "",
        limit: Int = 12,
    ): Result<List<LocalModelStore.ModelEntry>> = runCatching {
        val q = query.trim().ifBlank { "qwen" }
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
        val listUrl =
            "https://huggingface.co/api/models?search=$encoded&author=litert-community&limit=$limit&sort=downloads&direction=-1"
        val models = getJsonArray(listUrl, hfToken)
        buildList {
            for (i in 0 until models.length()) {
                val id = models.getJSONObject(i).optString("modelId")
                    .ifBlank { models.getJSONObject(i).optString("id") }
                if (id.isBlank()) continue
                val tasks = listTaskFiles(id, hfToken)
                tasks.forEach { file ->
                    add(toEntry(repoId = id, fileName = file.name, size = file.size))
                }
            }
        }.distinctBy { it.id }
    }

    private data class RemoteFile(val name: String, val size: Long)

    private fun listTaskFiles(repoId: String, hfToken: String): List<RemoteFile> {
        val url = "https://huggingface.co/api/models/$repoId/tree/main"
        val tree = runCatching { getJsonArray(url, hfToken) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until tree.length()) {
                val obj = tree.getJSONObject(i)
                if (obj.optString("type") != "file") continue
                val name = obj.optString("path").ifBlank { obj.optString("name") }
                if (!name.endsWith(".task", ignoreCase = true)) continue
                // 优先手机用非 web 包
                if (name.contains("-web.task", ignoreCase = true)) continue
                add(RemoteFile(name = name.substringAfterLast('/'), size = obj.optLong("size", 0L)))
            }
        }
    }

    private fun toEntry(repoId: String, fileName: String, size: Long): LocalModelStore.ModelEntry {
        val id = (repoId.substringAfterLast('/') + "-" + fileName.removeSuffix(".task"))
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
        val label = "${repoId.substringAfterLast('/')} · ${fileName.removeSuffix(".task")}"
        val url = "https://huggingface.co/$repoId/resolve/main/$fileName"
        val approx = size.takeIf { it > 0 } ?: (200L * 1024L * 1024L)
        return LocalModelStore.ModelEntry(
            id = id,
            label = label,
            fileName = fileName,
            url = url,
            minBytes = (approx * 0.3).toLong().coerceAtLeast(10L * 1024L * 1024L),
            approxBytes = approx,
            source = repoId,
        )
    }

    private fun getJsonArray(url: String, hfToken: String): JSONArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            if (hfToken.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $hfToken")
            }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HF HTTP $code · ${body.take(120)}")
            return JSONArray(body)
        } finally {
            connection.disconnect()
        }
    }
}
