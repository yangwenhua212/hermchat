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
        OpenUrlTool(context.applicationContext),
        WebSearchTool(context.applicationContext),
        ShareTextTool(context.applicationContext),
    ).associateBy { it.name }

    fun get(name: String): PhoneTool? = tools[name]

    fun riskFor(name: String): ToolRisk =
        tools[name]?.risk ?: ToolRisk.DESTRUCTIVE

    fun requiredPermissions(name: String): Array<String> =
        tools[name]?.requiredPermissions ?: emptyArray()

    /**
     * @param call.needConfirm 表示用户已授权（或 READ_ONLY 路径已放行）。
     * 写/破坏级工具未授权一律拒绝，防止远端 payload 静默执行。
     */
    suspend fun execute(call: ToolCall): ToolResult {
        val tool = tools[call.name]
            ?: return ToolResult(
                toolCallId = call.id,
                name = call.name,
                success = false,
                message = "未知工具：${call.name}",
            )
        if (tool.risk.requiresUserConfirm && !call.needConfirm) {
            return ToolResult(
                toolCallId = call.id,
                name = call.name,
                success = false,
                message = "需要确认后才能执行",
            )
        }
        return tool.execute(call)
    }
}
