package com.eraherm.hermchat.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Creates a system alarm via [AlarmClock.ACTION_SET_ALARM] (opens/sets clock app).
 */
class AlarmTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME

    /** SET_ALARM is a normal install-time permission. */
    override val requiredPermissions: Array<String> = emptyArray()

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        try {
            val message = call.arguments["message"]?.ifBlank { null } ?: "HxSync 提醒"
            val triggerAt = call.arguments["triggerMs"]?.toLongOrNull()
                ?: return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = false,
                    message = "缺少提醒时间",
                )
            val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(AlarmClock.EXTRA_VIBRATE, true)
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = false,
                    message = "本机没有可用的时钟应用",
                )
            }
            context.startActivity(intent)
            ToolResult(
                toolCallId = call.id,
                name = name,
                success = true,
                message = "已设置提醒「$message」· ${ToolCallParser.formatTime(triggerAt)}",
            )
        } catch (e: SecurityException) {
            ToolResult(
                toolCallId = call.id,
                name = name,
                success = false,
                message = "没有设置闹钟权限",
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = call.id,
                name = name,
                success = false,
                message = e.message ?: "设置提醒失败",
            )
        }
    }

    companion object {
        const val NAME = "alarm.create"
    }
}
