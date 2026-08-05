package com.eraherm.hermchat.tools.search

import com.eraherm.hermchat.data.network.SharedHttpClients
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * SearXNG 公共实例 JSON：`/search?q=&format=json`
 * 多实例轮换，任一成功即返回。
 */
class SearxngProvider(
    private val client: OkHttpClient = defaultClient(),
    private val instances: List<String> = DEFAULT_INSTANCES,
) : SearchProvider {
    override val id: String = "searxng"

    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) throw SearchProviderException("缺少搜索词")
        var lastError: Exception? = null
        for (base in instances) {
            try {
                val hits = searchOne(base.trimEnd('/'), q, limit)
                if (hits.isNotEmpty()) return hits
                lastError = SearchProviderException("空结果")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw SearchProviderException(
            lastError?.message ?: "SearXNG 不可用",
            lastError,
        )
    }

    private fun searchOne(base: String, query: String, limit: Int): List<SearchHit> {
        val url = "$base/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("language", "zh-CN")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SearchProviderException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw SearchProviderException("空响应")
            val json = JSONObject(body)
            val results = json.optJSONArray("results")
                ?: throw SearchProviderException("无 results")
            val hits = ArrayList<SearchHit>(limit)
            for (i in 0 until results.length()) {
                if (hits.size >= limit) break
                val item = results.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val link = item.optString("url").trim()
                    .ifBlank { item.optString("link").trim() }
                val snippet = item.optString("content").trim()
                    .ifBlank { item.optString("snippet").trim() }
                if (title.isBlank() && link.isBlank()) continue
                hits.add(SearchHit(title = title.ifBlank { link }, url = link, snippet = snippet))
            }
            return hits
        }
    }

    companion object {
        private const val USER_AGENT =
            "HxSync/1.0 (Android; personal assistant; +https://github.com/yangwenhua212/hermchat)"

        val DEFAULT_INSTANCES = listOf(
            "https://searx.be",
            "https://search.sapti.me",
            "https://searx.tiekoetter.com",
            "https://priv.au",
        )

        fun defaultClient(): OkHttpClient = SharedHttpClients.api.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
