package com.eraherm.hermchat.data.model

/** 工具请求来源：确认卡据此展示「谁在请求」。 */
enum class ToolOrigin {
    /** 手机端自身（本地 Loop / 聊天流夹带）。 */
    LOCAL,

    /** 远程 Agent 独立 tool_call 帧（v0.2.0 Agent Bridge）。 */
    REMOTE,
}

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, String> = emptyMap(),
    val needConfirm: Boolean = true,
    val title: String,
    val summary: String,
    val origin: ToolOrigin = ToolOrigin.LOCAL,
)

data class ToolResult(
    val toolCallId: String,
    val name: String,
    val success: Boolean,
    val message: String,
)
