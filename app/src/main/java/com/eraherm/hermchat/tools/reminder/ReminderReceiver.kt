package com.eraherm.hermchat.tools.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eraherm.hermchat.MainActivity
import com.eraherm.hermchat.R

/**
 * Fires a high-priority notification when [AlarmTool]'s local AlarmManager fallback triggers.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val message = intent?.getStringExtra(EXTRA_MESSAGE)?.ifBlank { null } ?: "提醒时间到了"
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("HxSync 提醒")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val id = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "hxsync_reminders"
        const val EXTRA_MESSAGE = "message"
    }
}
