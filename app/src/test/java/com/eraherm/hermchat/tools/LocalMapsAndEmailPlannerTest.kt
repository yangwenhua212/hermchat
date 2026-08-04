package com.eraherm.hermchat.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMapsAndEmailPlannerTest {
    @Test
    fun mapsNav() {
        val call = LocalMapsPlanner.plan("导航到北京南站")
        assertNotNull(call)
        assertEquals(MapsSearchTool.NAME, call!!.name)
        assertEquals("北京南站", call.arguments["query"])
    }

    @Test
    fun mapsSearchPrefix() {
        val call = LocalMapsPlanner.plan("地图搜星巴克")
        assertNotNull(call)
        assertEquals("星巴克", call!!.arguments["query"])
    }

    @Test
    fun emailTo() {
        val call = LocalEmailPlanner.plan("发邮件给 demo@example.com 主题 你好")
        assertNotNull(call)
        assertEquals(EmailComposeTool.NAME, call!!.name)
        assertEquals("demo@example.com", call.arguments["to"])
        assertEquals("你好", call.arguments["subject"])
    }

    @Test
    fun notEmailHowTo() {
        assertNull(LocalEmailPlanner.plan("怎么写邮件"))
        assertNull(LocalEmailPlanner.plan("帮我写邮件怎么发"))
    }

    @Test
    fun mailtoBuild() {
        val s = EmailComposeTool.buildMailto("a@b.com", "Hi", "Body")
        assertTrue(s.startsWith("mailto:a@b.com"))
        assertTrue(s.contains("subject="))
    }

    @Test
    fun notMapsCasual() {
        assertNull(LocalMapsPlanner.plan("今天天气怎么样"))
    }

    @Test
    fun openMapsSearchNotAppOpen() {
        val call = LocalToolPlanner.plan("打开地图搜星巴克")
        assertNotNull(call)
        assertEquals(MapsSearchTool.NAME, call!!.name)
        assertEquals("星巴克", call.arguments["query"])
    }
}
