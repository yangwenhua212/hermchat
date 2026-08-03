package com.eraherm.hermchat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.MainActivity
import com.eraherm.hermchat.R
import com.eraherm.hermchat.data.model.AgentKind

/**
 * 轻量前台服务：在 WebSocket / 需长连的 Agent 已连接时保持进程，降低回桌面即断线概率。
 * 不替代唤醒服务；职责仅是「保连接进程」。
 */
class BridgeKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val name = (application as? HermChatApp)?.agentStore?.currentAgent?.name ?: "助手"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("HxSync 保持连接")
            .setContentText("已连接 $name · 点开继续聊")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "连接保活",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "后台保持与电脑助手的连接"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "hxsync_bridge_keepalive"
        private const val NOTIFICATION_ID = 7102
        const val ACTION_STOP = "com.eraherm.hermchat.action.STOP_BRIDGE_KEEPALIVE"

        fun sync(context: Context) {
            runCatching {
                val app = context.applicationContext as? HermChatApp ?: return
                val holder = app.agentSessionHolder
                val want = holder.client != null && (
                    holder.agentKind == AgentKind.WEBSOCKET ||
                        holder.agentKind == AgentKind.CUSTOM ||
                        holder.agentKind == AgentKind.HERMES
                    )
                val intent = Intent(context, BridgeKeepAliveService::class.java)
                if (want) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    context.startService(Intent(intent).setAction(ACTION_STOP))
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, BridgeKeepAliveService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
