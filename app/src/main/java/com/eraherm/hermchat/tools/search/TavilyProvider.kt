package com.eraherm.hermchat.tools.search

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.eraherm.hermchat.data.network.SharedHttpClients
import java.util.concurrent.TimeUnit

/** Tavily Search：https://api.tavily.com/search */
class TavilyProvider(
    private val apiKey: String,
    private val client: OkHttpClient = defaultClient(),
    private val endpoint: String = "https://api.tavily.com/search",
) : SearchProvider {
    override val id: String = "tavily"

    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val q = query.trim()
        val key = apiKey.trim()
        if (q.isEmpty()) throw SearchProviderException("缺少搜索词")
        if (key.isEmpty()) throw SearchProviderException("缺少 Tavily key")
        val payload = JSONObject()
            .put("api_key", key)
            .put("query", q)
            .put("max_results", limit.coerceIn(1, 10))
            .put("search_depth", "basic")
            .put("include_answer", false)
            .toString()
        val request = Request.Builder()
            .url(endpoint)
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
            val results = root.optJSONArray("results")
                ?: throw SearchProviderException("无 results")
            val hits = ArrayList<SearchHit>(limit)
            for (i in 0 until results.length()) {
                if (hits.size >= limit) break
                val item = results.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val url = item.optString("url").trim()
                val snippet = item.optString("content").trim()
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

        fun defaultClient(): OkHttpClient = SharedHttpClients.api.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
