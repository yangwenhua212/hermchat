package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult

interface PhoneTool {
    val name: String
    val requiredPermissions: Array<String>

    suspend fun execute(call: ToolCall): ToolResult
}
