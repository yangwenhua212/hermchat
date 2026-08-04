package com.eraherm.hermchat.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAlarmPlannerTest {
    @Test
    fun halfHourRemind() {
        val call = LocalAlarmPlanner.plan("半小时后提醒我喝水")
        assertNotNull(call)
        assertEquals(AlarmTool.NAME, call!!.name)
        val trigger = call.arguments["triggerMs"]!!.toLong()
        val delta = trigger - System.currentTimeMillis()
        assertTrue(delta in 25 * 60_000L..35 * 60_000L)
    }

    @Test
    fun halfHourWithoutHou() {
        val call = LocalAlarmPlanner.plan("半小时提醒我一下")
        assertNotNull(call)
    }

    @Test
    fun minutesRemindOrder() {
        assertNotNull(LocalAlarmPlanner.plan("提醒我10分钟"))
        assertNotNull(LocalAlarmPlanner.plan("10分钟提醒我出门"))
    }

    @Test
    fun tomorrowMorningDefault8() {
        val call = LocalAlarmPlanner.plan("明天早上叫我")
        assertNotNull(call)
        val trigger = call!!.arguments["triggerMs"]!!.toLong()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(8, cal.get(java.util.Calendar.HOUR_OF_DAY))
    }

    @Test
    fun multiStepSkippedByLocalToolPlanner() {
        assertTrue(LocalToolPlanner.looksMultiStep("查天气然后半小时后提醒我"))
        assertNull(LocalToolPlanner.plan("查天气然后半小时后提醒我"))
    }

    @Test
    fun pureAlarmStillPlans() {
        assertNotNull(LocalToolPlanner.plan("半小时后提醒我"))
    }
}
