package com.eraherm.hermchat.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalUrlOpenPlannerTest {
    @Test
    fun openDeepSeekOfficial() {
        val call = LocalUrlOpenPlanner.plan("打开 DeepSeek 官网")
        assertNotNull(call)
        assertEquals(OpenUrlTool.NAME, call!!.name)
        assertEquals("https://www.deepseek.com/", call.arguments["url"])
    }

    @Test
    fun openBareDomain() {
        val call = LocalUrlOpenPlanner.plan("打开 example.org")
        assertNotNull(call)
        assertEquals("https://example.org/", call!!.arguments["url"])
    }

    @Test
    fun openDeepSeekDomainUsesKnown() {
        val call = LocalUrlOpenPlanner.plan("打开 deepseek.com")
        assertNotNull(call)
        assertEquals("https://www.deepseek.com/", call!!.arguments["url"])
    }

    @Test
    fun openFullUrl() {
        val call = LocalUrlOpenPlanner.plan("打开 https://www.example.com/path")
        assertNotNull(call)
        assertEquals("https://www.example.com/path", call!!.arguments["url"])
    }

    @Test
    fun notSearchWeather() {
        assertNull(LocalUrlOpenPlanner.plan("打开搜索天气"))
    }

    @Test
    fun appOpenStillWechat() {
        assertNull(LocalUrlOpenPlanner.plan("打开微信"))
        val app = LocalAppOpenPlanner.plan("打开微信")
        assertNotNull(app)
        assertEquals(AppOpenTool.NAME, app!!.name)
    }

    @Test
    fun openDouyinAppNotWebsite() {
        assertNull(LocalUrlOpenPlanner.plan("打开抖音"))
        assertNull(LocalUrlOpenPlanner.plan("打开抖音app"))
        val app = LocalAppOpenPlanner.plan("打开抖音app")
        assertNotNull(app)
        assertEquals(AppOpenTool.NAME, app!!.name)
        assertEquals("抖音", app.arguments["app"])
        val viaPlanner = LocalToolPlanner.plan("打开抖音app")
        assertNotNull(viaPlanner)
        assertEquals(AppOpenTool.NAME, viaPlanner!!.name)
    }

    @Test
    fun openDouyinOfficialStillUrl() {
        val call = LocalUrlOpenPlanner.plan("打开抖音官网")
        assertNotNull(call)
        assertEquals(OpenUrlTool.NAME, call!!.name)
        assertEquals("https://www.douyin.com/", call.arguments["url"])
    }

    @Test
    fun toolPlannerPrefersUrlOverApp() {
        val call = LocalToolPlanner.plan("打开DeepSeek官网")
        assertNotNull(call)
        assertEquals(OpenUrlTool.NAME, call!!.name)
    }

    @Test
    fun unknownOfficialSiteSearchesThenOpen() {
        val call = LocalUrlOpenPlanner.plan("帮我打开某某奇奇怪怪产品的官网")
        assertNotNull(call)
        assertEquals(WebSearchTool.NAME, call!!.name)
        assertEquals("某某奇奇怪怪产品 官网", call.arguments["query"])
        assertEquals(LocalUrlOpenPlanner.FOLLOW_UP_URL_OPEN, call.arguments["follow_up"])
    }

    @Test
    fun soraUsesKnownOrSearch() {
        val call = LocalUrlOpenPlanner.plan("帮我打开 Sora 的官网")
        assertNotNull(call)
        // 已知别名则直接打开；否则搜
        if (call!!.name == OpenUrlTool.NAME) {
            assertEquals("https://openai.com/sora", call.arguments["url"])
        } else {
            assertEquals(WebSearchTool.NAME, call.name)
            assertEquals(LocalUrlOpenPlanner.FOLLOW_UP_URL_OPEN, call.arguments["follow_up"])
        }
    }
}
