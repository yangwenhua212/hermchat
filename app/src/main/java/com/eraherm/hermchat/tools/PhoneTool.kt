package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult

interface PhoneTool {
    val name: String
    val requiredPermissions: Array<String>

    /** 默认写操作须确认；只读工具覆写为 [ToolRisk.READ_ONLY]。 */
    val risk: ToolRisk
        get() = ToolRisk.WRITE

    suspend fun execute(call: ToolCall): ToolResult
}
