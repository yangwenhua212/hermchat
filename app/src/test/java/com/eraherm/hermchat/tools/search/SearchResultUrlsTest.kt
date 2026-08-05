package com.eraherm.hermchat.tools.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResultUrlsTest {
    @Test
    fun firstUrlFromFormattedHits() {
        val text = """
            搜索结果（SearXNG）：
            1. Sora
            https://openai.com/sora
            简介
            """.trimIndent()
        assertEquals("https://openai.com/sora", SearchResultUrls.firstHttpUrl(text))
    }

    @Test
    fun none() {
        assertNull(SearchResultUrls.firstHttpUrl("没有链接"))
    }
}
