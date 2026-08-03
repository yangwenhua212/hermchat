package com.eraherm.hermchat.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallParserTest {

    @Test
    fun extract_calendarToolCall() {
        val text = """
            好的，帮你建日程。
            {"type":"tool_call","name":"calendar.create","arguments":{"title":"开会","beginMs":"1710000000000"},"need_confirm":true}
        """.trimIndent()
        val (display, call) = ToolCallParser.extract(text)
        assertNotNull(call)
        assertEquals("calendar.create", call!!.name)
        assertEquals("开会", call.arguments["title"])
        assertTrue(!call.needConfirm)
        assertTrue(display.contains("好的") || display.isNotBlank())
        assertTrue(!display.contains("tool_call"))
    }

    @Test
    fun extract_clipboardRead() {
        val text =
            """{"type":"tool_call","name":"clipboard.read","arguments":{}}"""
        val (display, call) = ToolCallParser.extract(text)
        assertNotNull(call)
        assertEquals("clipboard.read", call!!.name)
        assertEquals("好的。", display)
    }

    @Test
    fun extract_alarmToolCall() {
        val text =
            """{"type":"tool_call","name":"alarm.create","arguments":{"message":"喝水","triggerMs":"1710003600000"}}"""
        val (_, call) = ToolCallParser.extract(text)
        assertNotNull(call)
        assertEquals("alarm.create", call!!.name)
        assertEquals("喝水", call.arguments["message"])
    }

    @Test
    fun extract_noToolReturnsNull() {
        val (display, call) = ToolCallParser.extract("就是普通回复，没有工具。")
        assertNull(call)
        assertEquals("就是普通回复，没有工具。", display)
    }

    @Test
    fun summarize_alarm() {
        val summary = ToolCallParser.summarize(
            "alarm.create",
            mapOf("message" to "开会", "triggerMs" to "1710000000000"),
        )
        assertTrue(summary.contains("开会"))
    }
}
