package com.eraherm.hermchat.tools

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import com.eraherm.hermchat.tools.reminder.ReminderReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 设置提醒：优先系统时钟 App（SET_ALARM / SET_TIMER），失败则回退到本机 AlarmManager 通知。
 */
class AlarmTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME

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
            if (triggerAt <= System.currentTimeMillis() + 5_000L) {
                return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = false,
                    message = "提醒时间已过",
                )
            }

            val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val delaySec = ((triggerAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(1)

            // 1) 相对时间优先用系统倒计时（很多机型比闹钟更稳）
            if (delaySec in 1..(24 * 60 * 60)) {
                if (trySetTimer(message, delaySec)) {
                    return@withContext ToolResult(
                        toolCallId = call.id,
                        name = name,
                        success = true,
                        message = "已设置倒计时「$message」· ${ToolCallParser.formatTime(triggerAt)}",
                    )
                }
            }

            // 2) 系统闹钟
            if (trySetAlarm(message, hour, minute, skipUi = true) ||
                trySetAlarm(message, hour, minute, skipUi = false)
            ) {
                return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = true,
                    message = "已设置闹钟「$message」· ${ToolCallParser.formatTime(triggerAt)}",
                )
            }

            // 3) 本机通知闹钟（不依赖时钟 App）
            scheduleLocalReminder(message, triggerAt)
            ToolResult(
                toolCallId = call.id,
                name = name,
                success = true,
                message = "已用本机通知设置提醒「$message」· ${ToolCallParser.formatTime(triggerAt)}",
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

    private fun trySetTimer(message: String, lengthSec: Int): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_LENGTH, lengthSec)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return launchIfResolvable(intent) || launchIfResolvable(
            Intent(intent).apply { putExtra(AlarmClock.EXTRA_SKIP_UI, false) },
        )
    }

    private fun trySetAlarm(message: String, hour: Int, minute: Int, skipUi: Boolean): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
        }
        return launchIfResolvable(intent)
    }

    private fun launchIfResolvable(intent: Intent): Boolean {
        val pm = context.packageManager
        if (intent.resolveActivity(pm) == null) return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun scheduleLocalReminder(message: String, triggerAt: Long) {
        ensureChannel()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = (triggerAt xor message.hashCode().toLong()).toInt()
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        when {
            Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms() -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            Build.VERSION.SDK_INT >= 23 -> {
                runCatching {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }.getOrElse {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            }
            else -> alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ReminderReceiver.CHANNEL_ID,
            "提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "HxSync 本机提醒"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NAME = "alarm.create"
    }
}
