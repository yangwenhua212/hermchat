package com.eraherm.hermchat.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmToolParseTriggerTest {
    @Test
    fun millisPassthrough() {
        assertEquals(1_720_001_800_000L, AlarmTool.parseTriggerMs(mapOf("triggerMs" to "1720001800000")))
    }

    @Test
    fun secondsScaled() {
        assertEquals(1_720_001_800_000L, AlarmTool.parseTriggerMs(mapOf("triggerMs" to "1720001800")))
    }

    @Test
    fun aliasTriggerMs() {
        assertEquals(1_720_001_800_000L, AlarmTool.parseTriggerMs(mapOf("trigger_ms" to "1720001800000")))
    }

    @Test
    fun missing() {
        assertNull(AlarmTool.parseTriggerMs(emptyMap()))
    }
}
