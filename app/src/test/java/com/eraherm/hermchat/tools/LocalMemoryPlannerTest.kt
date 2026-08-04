package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.memory.LocalMemoryRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMemoryPlannerTest {
    @Test
    fun rememberPlease() {
        val call = LocalMemoryPlanner.plan("请记住我喜欢绿茶")
        assertNotNull(call)
        assertEquals(MemoryRememberTool.NAME, call!!.name)
        assertEquals("我喜欢绿茶", call.arguments["content"])
    }

    @Test
    fun rememberInline() {
        val call = LocalMemoryPlanner.plan("记住我叫小明")
        assertNotNull(call)
        assertEquals(MemoryRememberTool.NAME, call!!.name)
        assertEquals("我叫小明", call.arguments["content"])
    }

    @Test
    fun recallStill() {
        val call = LocalMemoryPlanner.plan("还记得我喜欢什么吗")
        assertNotNull(call)
        assertEquals(MemoryRecallTool.NAME, call!!.name)
        assertTrue(call!!.arguments["query"]!!.contains("喜欢"))
    }

    @Test
    fun notCasualChat() {
        assertNull(LocalMemoryPlanner.plan("今天天气怎么样"))
        assertNull(LocalMemoryPlanner.plan("半小时后提醒我开会"))
    }

    @Test
    fun rankPrefersOverlap() {
        data class Row(val content: String, val pinned: Boolean = false)
        val rows = listOf(
            Row("用户喜欢红茶"),
            Row("用户喜欢绿茶", pinned = true),
            Row("明天开会"),
        )
        val ranked = LocalMemoryRanker.rank(
            query = "喜欢喝什么茶",
            items = rows,
            topK = 2,
            contentOf = { it.content },
            pinnedOf = { it.pinned },
        )
        assertEquals(2, ranked.size)
        assertTrue(ranked[0].item.content.contains("绿茶") || ranked[0].item.content.contains("红茶"))
        assertTrue(ranked.all { it.score > 0 })
    }
}
