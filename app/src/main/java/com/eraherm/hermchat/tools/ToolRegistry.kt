package com.eraherm.hermchat.tools

import android.content.Context
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult

class ToolRegistry(
    context: Context,
) {
    private val tools: Map<String, PhoneTool> = listOf(
        CalendarTool(context.applicationContext),
        AlarmTool(context.applicationContext),
    ).associateBy { it.name }

    fun get(name: String): PhoneTool? = tools[name]

    fun requiredPermissions(name: String): Array<String> =
        tools[name]?.requiredPermissions ?: emptyArray()

    suspend fun execute(call: ToolCall): ToolResult {
        val tool = tools[call.name]
            ?: return ToolResult(
                toolCallId = call.id,
                name = call.name,
                success = false,
                message = "未知工具：${call.name}",
            )
        return tool.execute(call)
    }
}
