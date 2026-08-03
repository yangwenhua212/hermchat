package com.eraherm.hermchat.tools

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import com.eraherm.hermchat.tools.reminder.ReminderReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 设置提醒：优先系统倒计时/闹钟；不可靠时回退本机精确 AlarmManager + 通知。
 */
class AlarmTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME

    override val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

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

            // 1) ≤24h 相对时间：系统倒计时（先出 UI，OEM 更稳）
            if (delaySec in 1..(24 * 60 * 60)) {
                if (trySetTimer(message, delaySec)) {
                    return@withContext ToolResult(
                        toolCallId = call.id,
                        name = name,
                        success = true,
                        message = "已打开系统倒计时「$message」· ${ToolCallParser.formatTime(triggerAt)}",
                    )
                }
            }

            // 2) 仅当目标就是「下一次该时刻」时用 SET_ALARM（会丢日期，后天易变成明天）
            if (isNextOccurrenceOfClock(triggerAt, hour, minute)) {
                if (trySetAlarm(message, hour, minute, skipUi = false) ||
                    trySetAlarm(message, hour, minute, skipUi = true)
                ) {
                    return@withContext ToolResult(
                        toolCallId = call.id,
                        name = name,
                        success = true,
                        message = "已打开系统闹钟「$message」· ${ToolCallParser.formatTime(triggerAt)}",
                    )
                }
            }

            // 3) 本机精确提醒（保日期）
            return@withContext scheduleLocalReminder(call.id, message, triggerAt)
        } catch (e: SecurityException) {
            ToolResult(
                toolCallId = call.id,
                name = name,
                success = false,
                message = "没有设置闹钟权限，请在系统设置中允许精确闹钟与通知",
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
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return launchIfResolvable(intent) || launchIfResolvable(
            Intent(intent).apply { putExtra(AlarmClock.EXTRA_SKIP_UI, true) },
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

    private fun isNextOccurrenceOfClock(triggerAt: Long, hour: Int, minute: Int): Boolean {
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return kotlin.math.abs(next.timeInMillis - triggerAt) < 60_000L
    }

    private fun scheduleLocalReminder(
        toolCallId: String,
        message: String,
        triggerAt: Long,
    ): ToolResult {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                return ToolResult(
                    toolCallId = toolCallId,
                    name = name,
                    success = false,
                    message = "需要通知权限才能用本机提醒，请在系统设置中开启 HxSync 通知",
                )
            }
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return ToolResult(
                toolCallId = toolCallId,
                name = name,
                success = false,
                message = "通知已关闭，请在系统设置中开启 HxSync 通知",
            )
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        data = android.net.Uri.parse("package:${context.packageName}")
                    },
                )
            }
            return ToolResult(
                toolCallId = toolCallId,
                name = name,
                success = false,
                message = "需要「精确闹钟」权限，请在刚打开的设置页允许后再试一次",
            )
        }

        ensureChannel()
        val requestCode = (triggerAt xor message.hashCode().toLong()).toInt()
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            ToolResult(
                toolCallId = toolCallId,
                name = name,
                success = true,
                message = "已用本机通知设置提醒「$message」· ${ToolCallParser.formatTime(triggerAt)}",
            )
        } catch (e: SecurityException) {
            ToolResult(
                toolCallId = toolCallId,
                name = name,
                success = false,
                message = "系统拒绝精确闹钟：${e.message ?: "请到设置里允许"}",
            )
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
