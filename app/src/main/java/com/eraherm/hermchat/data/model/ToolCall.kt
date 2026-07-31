package com.eraherm.hermchat.data.model

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, String> = emptyMap(),
    val needConfirm: Boolean = true,
    val title: String,
    val summary: String,
)

data class ToolResult(
    val toolCallId: String,
    val name: String,
    val success: Boolean,
    val message: String,
)
