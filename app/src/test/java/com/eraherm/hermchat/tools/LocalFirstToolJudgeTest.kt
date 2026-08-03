package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFirstToolJudgeTest {
    @Test
    fun accept_validToolCall() {
        val tool = ToolCall(
            id = "1",
            name = "alarm.create",
            arguments = mapOf("message" to "喝水", "triggerMs" to "1"),
            needConfirm = false,
            title = "提醒",
            summary = "半小时后",
        )
        assertTrue(LocalFirstToolJudge.accept("""好的 {"type":"tool_call","name":"alarm.create","arguments":{}}""", tool))
    }

    @Test
    fun accept_plainChat() {
        assertTrue(LocalFirstToolJudge.accept("在。有什么事？", null))
    }

    @Test
    fun reject_weakLocal() {
        assertFalse(LocalFirstToolJudge.accept("本地模型未就绪。请在配置里下载模型后再聊。", null))
    }

    @Test
    fun reject_brokenToolJson() {
        assertFalse(
            LocalFirstToolJudge.accept(
                "我来设闹钟 tool_call name=alarm.create 半小时",
                null,
            ),
        )
    }

    @Test
    fun reject_empty() {
        assertFalse(LocalFirstToolJudge.accept(" ", null))
    }
}
