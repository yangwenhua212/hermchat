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
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.MainActivity
import com.eraherm.hermchat.R
import com.eraherm.hermchat.data.local.WakeEngineKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: VoiceEngine? = null
    private var listeningEvents = false
    private var preparingOffline = false

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

        if (intent?.action == ACTION_IN_APP) {
            val direct = intent.getBooleanExtra(EXTRA_IN_APP_DIRECT, false)
            if (engine == null && app.wakeSettingsStore.settings.value.enabled) {
                // 进程刚起来：先建立监听再切模式
                bootstrapEngine(app, pushToTalk = false)
            }
            engine?.setInAppDirectListen(direct)
            if (direct) {
                updateNotification("请说指令…")
            } else if (app.wakeSettingsStore.settings.value.enabled) {
                updateNotification("正在听「${app.wakeSettingsStore.settings.value.phrase}」")
            }
            return START_STICKY
        }

        val pushToTalk = intent?.action == ACTION_PTT
        if (!pushToTalk) {
            app.wakeSettingsStore.update { it.copy(enabled = true) }
        }
        bootstrapEngine(app, pushToTalk = pushToTalk)
        return START_STICKY
    }

    private fun bootstrapEngine(app: HermChatApp, pushToTalk: Boolean) {
        val engineKind = app.wakeSettingsStore.preferredEngine()
        val phrase = app.wakeSettingsStore.settings.value.phrase
        when (engineKind) {
            WakeEngineKind.SYSTEM -> {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    app.voiceEventBus.emit(VoiceEvent.Error("本机暂无系统语音识别"))
                    app.wakeSettingsStore.update { it.copy(enabled = false) }
                    stopSelfSafe()
                    return
                }
                promoteForeground(if (pushToTalk) "请说指令…" else "准备听「$phrase」…")
                observeEventsOnce(app)
                ensureSystemEngine()
                if (pushToTalk) {
                    engine?.startPushToTalk()
                    updateNotification("请说指令…")
                } else {
                    engine?.startListeningLoop()
                    updateNotification("正在听「$phrase」")
                }
            }
            WakeEngineKind.OFFLINE -> {
                promoteForeground(if (pushToTalk) "请说指令…" else "准备唤醒模型…")
                observeEventsOnce(app)
                startOfflineSession(app, pushToTalk = pushToTalk)
            }
        }
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        // 不清除 enabled：进程被杀后仍表示「希望监听」，开机自启可据此恢复
        scope.cancel()
        super.onDestroy()
    }

    private fun startOfflineSession(app: HermChatApp, pushToTalk: Boolean) {
        val existing = engine as? SherpaWakeEngine
        if (existing != null) {
            if (pushToTalk) existing.startPushToTalk() else existing.startListeningLoop()
            return
        }
        if (preparingOffline) return
        preparingOffline = true
        scope.launch {
            val kwsInstaller = KwsModelInstaller(this@WakeWordService)
            val asrInstaller = AsrModelInstaller(this@WakeWordService)
            val prepared = withContext(Dispatchers.IO) {
                runCatching {
                    if (!kwsInstaller.isReady()) {
                        app.voiceEventBus.emit(VoiceEvent.Status("正在下载唤醒模型"))
                        updateNotification("正在下载唤醒模型…")
                        kwsInstaller.ensureInstalled { name ->
                            val line = "下载 $name"
                            app.voiceEventBus.emit(VoiceEvent.Status(line))
                            updateNotification(line)
                        }.getOrThrow()
                    }
                    if (!asrInstaller.isReady()) {
                        app.voiceEventBus.emit(VoiceEvent.Status("正在下载指令模型"))
                        updateNotification("正在下载指令模型…")
                        asrInstaller.ensureInstalled { progress ->
                            val line = progress.statusLine()
                            app.voiceEventBus.emit(VoiceEvent.Status(line))
                            updateNotification(line)
                        }.getOrThrow()
                    }
                    Pair(kwsInstaller.modelDir(), asrInstaller.modelDir())
                }
            }
            preparingOffline = false
            prepared.onSuccess { (kwsDir, asrDir) ->
                engine?.stop()
                val created = SherpaWakeEngine(
                    context = this@WakeWordService,
                    kwsModelDir = kwsDir,
                    asrModelDir = asrDir,
                    phraseProvider = { app.wakeSettingsStore.settings.value.phrase },
                    autoSendProvider = { app.wakeSettingsStore.settings.value.autoSend },
                    bus = app.voiceEventBus,
                )
                engine = created
                if (pushToTalk) {
                    created.startPushToTalk()
                } else {
                    created.startListeningLoop()
                }
                val phrase = app.wakeSettingsStore.settings.value.phrase
                updateNotification("正在听「$phrase」")
                app.voiceEventBus.emit(VoiceEvent.Status("正在听「$phrase」"))
            }.onFailure { err ->
                app.voiceEventBus.emit(VoiceEvent.Error(err.message ?: "离线模型下载失败"))
                app.wakeSettingsStore.update { it.copy(enabled = false) }
                stopSelfSafe()
            }
        }
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
                        updateNotification("请说指令")
                    }
                    is VoiceEvent.Transcript -> {
                        if (event.autoSend) {
                            updateNotification("正在问助手…")
                        } else if (app.wakeSettingsStore.settings.value.enabled) {
                            updateNotification("正在听「${app.wakeSettingsStore.settings.value.phrase}」")
                        }
                        // 仅点麦、未开后台监听：说完后收起服务
                        if (!app.wakeSettingsStore.settings.value.enabled) {
                            scope.launch {
                                delay(900)
                                if (!app.wakeSettingsStore.settings.value.enabled) {
                                    engine?.stop()
                                    engine = null
                                    stopForeground(STOP_FOREGROUND_REMOVE)
                                    stopSelf()
                                }
                            }
                        }
                    }
                    is VoiceEvent.Status -> {
                        updateNotification(event.message)
                        if (!event.message.startsWith("正在")) {
                            scope.launch {
                                delay(2_800)
                                val settings = app.wakeSettingsStore.settings.value
                                if (settings.enabled) {
                                    updateNotification("正在听「${settings.phrase}」")
                                }
                            }
                        }
                    }
                    is VoiceEvent.Error -> updateNotification(event.message)
                }
            }
        }
    }

    private fun ensureSystemEngine(): VoiceEngine {
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
        preparingOffline = false
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
        )
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
        const val ACTION_IN_APP = "com.eraherm.hermchat.wake.IN_APP"
        const val EXTRA_IN_APP_DIRECT = "in_app_direct"

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

        /** 聊天页前台：免唤醒词；退到后台：恢复喊唤醒词。 */
        fun setInAppDirectListen(context: Context, enabled: Boolean) {
            val app = context.applicationContext as? HermChatApp ?: return
            if (!app.wakeSettingsStore.settings.value.enabled && !enabled) return
            if (!app.wakeSettingsStore.settings.value.enabled) return
            val intent = Intent(context, WakeWordService::class.java)
                .setAction(ACTION_IN_APP)
                .putExtra(EXTRA_IN_APP_DIRECT, enabled)
            context.startForegroundService(intent)
        }
    }
}
