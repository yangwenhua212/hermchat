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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.MainActivity
import com.eraherm.hermchat.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: SpeechWakeEngine? = null
    private var listeningEvents = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafe()
            return START_NOT_STICKY
        }

        val app = application as HermChatApp
        val phrase = app.wakeSettingsStore.settings.value.phrase
        promoteForeground("正在听「$phrase」")
        app.wakeSettingsStore.update { it.copy(enabled = true) }
        observeEventsOnce(app)
        ensureEngine()

        when (intent?.action) {
            ACTION_PTT -> engine?.startPushToTalk()
            else -> engine?.startListeningLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        (application as? HermChatApp)?.wakeSettingsStore?.update { it.copy(enabled = false) }
        scope.cancel()
        super.onDestroy()
    }

    private fun promoteForeground(content: String) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(content),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
    }

    private fun observeEventsOnce(app: HermChatApp) {
        if (listeningEvents) return
        listeningEvents = true
        scope.launch {
            app.voiceEventBus.events.collect { event ->
                when (event) {
                    is VoiceEvent.WakeDetected -> {
                        vibrate()
                        updateNotification("在呢 · 请说指令")
                    }
                    is VoiceEvent.Status -> updateNotification(event.message)
                    is VoiceEvent.Transcript -> {
                        val phrase = app.wakeSettingsStore.settings.value.phrase
                        updateNotification("正在听「$phrase」")
                    }
                    is VoiceEvent.Error -> updateNotification(event.message)
                }
            }
        }
    }

    private fun ensureEngine(): SpeechWakeEngine {
        engine?.let { return it }
        val app = application as HermChatApp
        return SpeechWakeEngine(
            context = this,
            phraseProvider = { app.wakeSettingsStore.settings.value.phrase },
            autoSendProvider = { app.wakeSettingsStore.settings.value.autoSend },
            bus = app.voiceEventBus,
        ).also { engine = it }
    }

    private fun stopSelfSafe() {
        engine?.stop()
        engine = null
        (application as? HermChatApp)?.wakeSettingsStore?.update { it.copy(enabled = false) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun vibrate() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VibratorManager::class.java)
                manager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(80)
                }
            }
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "唤醒监听",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "HxSync 前台监听预设唤醒词"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HxSync")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(launch)
            .setOngoing(true)
            .addAction(0, "停止", stop)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    companion object {
        const val CHANNEL_ID = "hermchat_wake"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.eraherm.hermchat.wake.STOP"
        const val ACTION_START = "com.eraherm.hermchat.wake.START"
        const val ACTION_PTT = "com.eraherm.hermchat.wake.PTT"

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WakeWordService::class.java).setAction(ACTION_STOP),
            )
        }

        fun pushToTalk(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).setAction(ACTION_PTT)
            context.startForegroundService(intent)
        }
    }
}
