package com.eraherm.hermchat.tools.search

import com.eraherm.hermchat.data.network.SharedHttpClients
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * DuckDuckGo HTML 接口兜底。结构易变，仅作 SearXNG 失败后的备用。
 */
class DuckDuckGoHtmlProvider(
    private val client: OkHttpClient = defaultClient(),
) : SearchProvider {
    override val id: String = "duckduckgo"

    override suspend fun search(query: String, limit: Int): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) throw SearchProviderException("缺少搜索词")
        val body = FormBody.Builder()
            .add("q", q)
            .add("b", "")
            .build()
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SearchProviderException("HTTP ${response.code}")
            }
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) throw SearchProviderException("空响应")
            val hits = parse(html, limit)
            if (hits.isEmpty()) throw SearchProviderException("空结果")
            return hits
        }
    }

    private fun parse(html: String, limit: Int): List<SearchHit> {
        val hits = ArrayList<SearchHit>(limit)
        val resultBlock = Pattern.compile(
            """class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        ).matcher(html)
        val snippetPat = Pattern.compile(
            """class="result__snippet"[^>]*>(.*?)</(?:a|td|span)>""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )
        while (resultBlock.find() && hits.size < limit) {
            val rawHref = resultBlock.group(1).orEmpty()
            val titleHtml = resultBlock.group(2).orEmpty()
            val url = unwrapDuckUrl(rawHref)
            val title = stripTags(titleHtml).trim()
            if (url.isBlank() || title.isBlank()) continue
            var snippet = ""
            val snipMatcher = snippetPat.matcher(html)
            if (snipMatcher.find(resultBlock.end()) &&
                snipMatcher.start() - resultBlock.end() < 800
            ) {
                snippet = stripTags(snipMatcher.group(1).orEmpty()).trim()
            }
            hits.add(SearchHit(title = title, url = url, snippet = snippet))
        }
        if (hits.isEmpty()) {
            val uddg = Pattern.compile(
                """uddg=([^&"]+)""",
                Pattern.CASE_INSENSITIVE,
            ).matcher(html)
            val seen = LinkedHashSet<String>()
            while (uddg.find() && hits.size < limit) {
                val decoded = runCatching {
                    URLDecoder.decode(uddg.group(1), StandardCharsets.UTF_8.name())
                }.getOrNull().orEmpty()
                if (decoded.startsWith("http") && seen.add(decoded)) {
                    hits.add(SearchHit(title = decoded, url = decoded, snippet = ""))
                }
            }
        }
        return hits
    }

    private fun unwrapDuckUrl(href: String): String {
        val idx = href.indexOf("uddg=")
        if (idx >= 0) {
            val encoded = href.substring(idx + 5).substringBefore('&')
            return runCatching {
                URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
            }.getOrDefault(href)
        }
        return if (href.startsWith("http")) href else ""
    }

    private fun stripTags(raw: String): String =
        raw.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = SharedHttpClients.api.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
