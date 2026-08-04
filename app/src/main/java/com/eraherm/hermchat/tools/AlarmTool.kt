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
 * 设置提醒：优先唤起**系统时钟**的倒计时/闹钟（会出现在系统闹钟 App 里）；
 * 仅当系统时钟不可用时，才回退 HxSync 本机通知（不会进系统闹钟列表）。
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
            val triggerAt = parseTriggerMs(call.arguments)
                ?: return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = false,
                    message = "缺少提醒时间（需要 triggerMs 毫秒时间戳）",
                )
            if (triggerAt <= System.currentTimeMillis() + 5_000L) {
                return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = false,
                    message = "提醒时间已过，请换一个更晚的时间",
                )
            }

            val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val delaySec = ((triggerAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(1)
            val whenText = ToolCallParser.formatTime(triggerAt)

            // 1) 对准「今天/明天该钟点」：优先系统闹钟（会出现在时钟 App）
            if (isNextOccurrenceOfClock(triggerAt, hour, minute)) {
                if (trySetAlarm(message, hour, minute)) {
                    return@withContext ToolResult(
                        toolCallId = call.id,
                        name = name,
                        success = true,
                        message = "已请求系统闹钟「$message」· $whenText",
                    )
                }
            }

            // 2) ≤24h：系统倒计时
            if (delaySec in 1..(24 * 60 * 60)) {
                if (trySetTimer(message, delaySec)) {
                    return@withContext ToolResult(
                        toolCallId = call.id,
                        name = name,
                        success = true,
                        message = "已请求系统倒计时「$message」· $whenText",
                    )
                }
            }

            // 3) 钟点稍有偏差时再试一次 SET_ALARM（不少机型仍能设）
            if (delaySec in 1..(36 * 60 * 60) && trySetAlarm(message, hour, minute)) {
                return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = true,
                    message = "已请求系统闹钟「$message」· $whenText",
                )
            }

            // 4) 本机通知回退（明确告知：不是系统闹钟）
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
        val base = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, lengthSec)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            // true：尽量直接写入，避免只打开时钟 UI、用户没点保存就以为失败
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return launchClockIntent(base)
    }

    private fun trySetAlarm(message: String, hour: Int, minute: Int): Boolean {
        val base = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
        }
        return launchClockIntent(base)
    }

    /** 默认解析 + 常见时钟包名点名，提高国产机命中率。 */
    private fun launchClockIntent(template: Intent): Boolean {
        val variants = buildList {
            add(Intent(template).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            for (pkg in CLOCK_PACKAGES) {
                add(
                    Intent(template).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
        for (intent in variants) {
            if (!canHandle(intent)) continue
            val ok = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private fun canHandle(intent: Intent): Boolean {
        val pm = context.packageManager
        if (intent.resolveActivity(pm) != null) return true
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        return pm.queryIntentActivities(intent, flags).isNotEmpty()
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
        return kotlin.math.abs(next.timeInMillis - triggerAt) < 90_000L
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
                    message = "系统时钟不可用，且需要通知权限才能用 HxSync 提醒。请开启通知，或安装/启用系统时钟后再试",
                )
            }
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return ToolResult(
                toolCallId = toolCallId,
                name = name,
                success = false,
                message = "系统时钟不可用，且通知已关闭。请开启 HxSync 通知，或启用系统时钟后再试",
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
                message = "需要「精确闹钟」权限才能用 HxSync 通知提醒；系统时钟也未能打开。请在设置里允许后再试",
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
                @Suppress("DEPRECATION")
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            ToolResult(
                toolCallId = toolCallId,
                name = name,
                success = true,
                message = "未能打开系统时钟，已用 HxSync 通知提醒「$message」· " +
                    "${ToolCallParser.formatTime(triggerAt)}（不会出现在系统闹钟列表）",
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
            description = "HxSync 本机提醒（系统时钟不可用时的回退）"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NAME = "alarm.create"

        /** 接受 triggerMs / trigger_ms；秒级时间戳自动 ×1000。 */
        fun parseTriggerMs(args: Map<String, String>): Long? {
            val raw = args["triggerMs"]?.ifBlank { null }
                ?: args["trigger_ms"]?.ifBlank { null }
                ?: args["time"]?.ifBlank { null }
                ?: return null
            val value = raw.trim().toLongOrNull() ?: raw.trim().toDoubleOrNull()?.toLong() ?: return null
            // 1e12 ms ≈ 2001 年；更小多半是秒
            return if (value in 1_000_000_000L..9_999_999_999L) value * 1000L else value
        }

        /** 常见系统/厂商时钟包，用于 SET_ALARM / SET_TIMER 点名唤起。 */
        private val CLOCK_PACKAGES = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.huawei.deskclock",
            "com.hihonor.deskclock",
            "com.miui.deskclock",
            "com.android.alarmclock",
            "com.coloros.alarmclock",
            "com.oneplus.deskclock",
            "com.oplus.alarmclock",
            "com.vivo.deskclock",
            "com.bbk.timer",
        )
    }
}
