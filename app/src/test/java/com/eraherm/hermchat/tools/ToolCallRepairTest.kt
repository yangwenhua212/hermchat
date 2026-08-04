package com.eraherm.hermchat.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallRepairTest {
    @Test
    fun brokenJsonTriggersRetry() {
        assertTrue(
            ToolCallRepair.shouldRetry(
                userPrompt = "半小时后提醒我",
                assistantRaw = """好的 {"type":"tool_call","name":"alarm.create" """,
                parsedTool = null,
                localFallback = null,
            ),
        )
    }

    @Test
    fun fakeSuccessTriggersRetry() {
        assertTrue(
            ToolCallRepair.shouldRetry(
                userPrompt = "设个闹钟明天早上8点",
                assistantRaw = "好的，已设置闹钟。",
                parsedTool = null,
                localFallback = null,
            ),
        )
    }

    @Test
    fun localFallbackSkipsRetry() {
        val fb = LocalAlarmPlanner.plan("半小时后提醒我")
        assertTrue(fb != null)
        assertFalse(
            ToolCallRepair.shouldRetry(
                userPrompt = "半小时后提醒我",
                assistantRaw = "好的。",
                parsedTool = null,
                localFallback = fb,
            ),
        )
    }

    @Test
    fun howToAskDoesNotExpectTool() {
        assertFalse(ToolCallRepair.expectsPhoneTool("怎么设置闹钟"))
    }

    @Test
    fun codeFenceExtract() {
        val raw = """
            好的
            ```json
            {"type":"tool_call","name":"alarm.create","arguments":{"message":"喝水","triggerMs":1720001800000},"title":"提醒","summary":"半小时后"}
            ```
        """.trimIndent()
        val (_, tool) = ToolCallParser.extract(raw)
        assertTrue(tool != null)
        assertTrue(tool!!.name == AlarmTool.NAME)
    }
}
