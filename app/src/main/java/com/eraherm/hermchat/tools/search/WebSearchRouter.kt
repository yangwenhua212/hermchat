package com.eraherm.hermchat.tools.search

/**
 * 搜索源链：博查 → Tavily → SearXNG → DuckDuckGo（有 key 才加入付费源）。
 * 单次工具调用内顺序尝试，成功即停；不改设置持久态。
 */
object WebSearchRouter {
    data class Outcome(
        val hits: List<SearchHit>,
        val providerId: String,
        /** 是否用了非链头源（降级成功） */
        val degraded: Boolean,
    )

    fun buildChain(
        bochaKey: String?,
        tavilyKey: String?,
        searxng: SearchProvider = SearxngProvider(),
        duckduckgo: SearchProvider = DuckDuckGoHtmlProvider(),
        bochaFactory: (String) -> SearchProvider = { BochaProvider(it) },
        tavilyFactory: (String) -> SearchProvider = { TavilyProvider(it) },
    ): List<SearchProvider> {
        val chain = ArrayList<SearchProvider>(4)
        bochaKey?.trim()?.takeIf { it.isNotEmpty() }?.let { chain.add(bochaFactory(it)) }
        tavilyKey?.trim()?.takeIf { it.isNotEmpty() }?.let { chain.add(tavilyFactory(it)) }
        chain.add(searxng)
        chain.add(duckduckgo)
        return chain
    }

    suspend fun search(
        query: String,
        bochaKey: String?,
        tavilyKey: String?,
        limit: Int = 5,
        chain: List<SearchProvider> = buildChain(bochaKey, tavilyKey),
    ): Outcome {
        if (chain.isEmpty()) throw SearchProviderException("无可用搜索源")
        var lastError: Exception? = null
        chain.forEachIndexed { index, provider ->
            try {
                val hits = provider.search(query, limit)
                if (hits.isNotEmpty()) {
                    return Outcome(
                        hits = hits,
                        providerId = provider.id,
                        degraded = index > 0,
                    )
                }
                lastError = SearchProviderException("${provider.id}: 空结果")
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw SearchProviderException(
            lastError?.message ?: "搜索失败",
            lastError,
        )
    }

    fun formatHits(hits: List<SearchHit>, providerId: String, maxChars: Int = 1800): String {
        val body = buildString {
            append("搜索结果（")
            append(providerLabel(providerId))
            append("）：\n")
            hits.forEachIndexed { i, hit ->
                append(i + 1)
                append(". ")
                append(hit.title.take(80))
                if (hit.url.isNotBlank()) {
                    append('\n')
                    append(hit.url.take(200))
                }
                if (hit.snippet.isNotBlank()) {
                    append('\n')
                    append(hit.snippet.take(220))
                }
                append("\n\n")
            }
        }.trim()
        return if (body.length <= maxChars) body else body.take(maxChars - 1) + "…"
    }

    fun providerLabel(id: String): String = when (id) {
        "bocha" -> "博查"
        "tavily" -> "Tavily"
        "searxng" -> "SearXNG"
        "duckduckgo" -> "DuckDuckGo"
        else -> id
    }
}
