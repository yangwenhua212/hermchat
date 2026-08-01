package com.eraherm.hermchat.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSpeakerPrepareTest {
    @Test
    fun stripsToolCallJson() {
        val raw = """好的，我来设闹钟。
{"type":"tool_call","name":"alarm.create","arguments":{"message":"喝水","triggerMs":1}}
"""
        val out = TtsSpeaker.prepare(raw)
        assertTrue(out.contains("设闹钟"))
        assertFalse(out.contains("tool_call"))
    }

    @Test
    fun keepsPlainChinese() {
        val out = TtsSpeaker.prepare("今天天气不错，适合出门散步。")
        assertTrue(out.contains("天气不错"))
    }
}
