package com.eraherm.hermchat.tools.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchRouterTest {
    private class FakeProvider(
        override val id: String,
        private val result: List<SearchHit>? = null,
        private val error: String? = null,
    ) : SearchProvider {
        override suspend fun search(query: String, limit: Int): List<SearchHit> {
            error?.let { throw SearchProviderException(it) }
            return result.orEmpty()
        }
    }

    @Test
    fun buildChain_orderWithKeys() {
        val chain = WebSearchRouter.buildChain(
            bochaKey = "b",
            tavilyKey = "t",
            searxng = FakeProvider("searxng"),
            duckduckgo = FakeProvider("duckduckgo"),
            bochaFactory = { FakeProvider("bocha") },
            tavilyFactory = { FakeProvider("tavily") },
        )
        assertEquals(listOf("bocha", "tavily", "searxng", "duckduckgo"), chain.map { it.id })
    }

    @Test
    fun buildChain_noKeys() {
        val chain = WebSearchRouter.buildChain(
            bochaKey = null,
            tavilyKey = "  ",
            searxng = FakeProvider("searxng"),
            duckduckgo = FakeProvider("duckduckgo"),
        )
        assertEquals(listOf("searxng", "duckduckgo"), chain.map { it.id })
    }

    @Test
    fun search_degradesToSecond() = kotlinx.coroutines.runBlocking {
        val chain = listOf(
            FakeProvider("bocha", error = "限流"),
            FakeProvider(
                "searxng",
                result = listOf(SearchHit("t", "https://example.com", "s")),
            ),
        )
        val out = WebSearchRouter.search("q", null, null, chain = chain)
        assertEquals("searxng", out.providerId)
        assertTrue(out.degraded)
        assertEquals(1, out.hits.size)
    }

    @Test
    fun search_firstSuccessNotDegraded() = kotlinx.coroutines.runBlocking {
        val chain = listOf(
            FakeProvider(
                "searxng",
                result = listOf(SearchHit("t", "https://example.com")),
            ),
            FakeProvider("duckduckgo", error = "不应调用"),
        )
        val out = WebSearchRouter.search("q", null, null, chain = chain)
        assertEquals("searxng", out.providerId)
        assertFalse(out.degraded)
    }

    @Test
    fun formatHits_includesProvider() {
        val text = WebSearchRouter.formatHits(
            listOf(SearchHit("标题", "https://a.com", "摘要")),
            "searxng",
        )
        assertTrue(text.contains("SearXNG"))
        assertTrue(text.contains("标题"))
        assertTrue(text.contains("https://a.com"))
    }
}
