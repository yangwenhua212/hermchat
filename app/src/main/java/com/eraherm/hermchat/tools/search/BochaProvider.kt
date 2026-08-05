package com.eraherm.hermchat.tools.search

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 博查 Web Search：https://api.bochaai.com/v1/web-search */
class BochaProvider(
    private val apiKey: String,
    private val client: OkHttpClient = defaultClient(),
    private val endpoint: String = "https://api.bochaai.com/v1/web-search",
) : SearchProvider {
    override val id: String = "bocha"

    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val q = query.trim()
        val key = apiKey.trim()
        if (q.isEmpty()) throw SearchProviderException("缺少搜索词")
        if (key.isEmpty()) throw SearchProviderException("缺少博查 key")
        val payload = JSONObject()
            .put("query", q)
            .put("count", limit.coerceIn(1, 20))
            .put("summary", true)
            .put("freshness", "noLimit")
            .toString()
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SearchProviderException("HTTP ${response.code}: ${body.take(120)}")
            }
            if (body.isBlank()) throw SearchProviderException("空响应")
            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: root
            if (root.has("code") && root.optInt("code") !in listOf(0, 200)) {
                throw SearchProviderException(root.optString("msg").ifBlank { "博查错误" })
            }
            val webPages = data.optJSONObject("webPages")
                ?: throw SearchProviderException("无 webPages")
            val values = webPages.optJSONArray("value")
                ?: throw SearchProviderException("无结果")
            val hits = ArrayList<SearchHit>(limit)
            for (i in 0 until values.length()) {
                if (hits.size >= limit) break
                val item = values.optJSONObject(i) ?: continue
                val title = item.optString("name").trim()
                    .ifBlank { item.optString("title").trim() }
                val url = item.optString("url").trim()
                val snippet = item.optString("summary").trim()
                    .ifBlank { item.optString("snippet").trim() }
                if (title.isBlank() && url.isBlank()) continue
                hits.add(SearchHit(title = title.ifBlank { url }, url = url, snippet = snippet))
            }
            if (hits.isEmpty()) throw SearchProviderException("空结果")
            return hits
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
