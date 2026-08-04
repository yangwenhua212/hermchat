package com.eraherm.hermchat.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAppAndDialPlannerTest {
    @Test
    fun openWechat() {
        val call = LocalAppOpenPlanner.plan("打开微信")
        assertNotNull(call)
        assertEquals(AppOpenTool.NAME, call!!.name)
        assertEquals("微信", call.arguments["app"])
    }

    @Test
    fun dialNumber() {
        val call = LocalDialPlanner.plan("拨打 10086")
        assertNotNull(call)
        assertEquals(PhoneDialTool.NAME, call!!.name)
        assertEquals("10086", call.arguments["number"])
    }

    @Test
    fun dialWithDashes() {
        val call = LocalDialPlanner.plan("打电话 138-0013-8000")
        assertNotNull(call)
        assertEquals("13800138000", call!!.arguments["number"])
    }

    @Test
    fun notOpenSearch() {
        assertNull(LocalAppOpenPlanner.plan("打开搜索天气"))
    }

    @Test
    fun normalizeNumberPlus() {
        assertEquals("+8613800138000", PhoneDialTool.normalizeNumber("+86 138-0013-8000"))
    }
}
