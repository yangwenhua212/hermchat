package com.eraherm.hermchat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.MainActivity
import com.eraherm.hermchat.R
import com.eraherm.hermchat.data.model.Message
import com.eraherm.hermchat.data.model.MessageRole
import com.eraherm.hermchat.data.network.AIClientFactory
import com.eraherm.hermchat.tools.ToolCallParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * 后台唤醒识别到指令后，自动发给当前云端 / 本地 Agent。
 * 若聊天页 ViewModel 存活则走同一会话；否则独立请求并通知栏展示回复。
 */
class VoiceCloudBridge(
    private val app: HermChatApp,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val foregroundSender = AtomicReference<((String) -> Unit)?>(null)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            app.voiceEventBus.events.collect { event ->
                if (event is VoiceEvent.Transcript && event.autoSend) {
                    val text = event.text.trim()
                    if (text.isNotEmpty()) {
                        handleAutoSend(text)
                    }
                }
            }
        }
    }

    fun bindForegroundSender(sender: (String) -> Unit) {
        foregroundSender.set(sender)
    }

    fun unbindForegroundSender(sender: (String) -> Unit) {
        foregroundSender.compareAndSet(sender, null)
    }

    private fun handleAutoSend(text: String) {
        scope.launch {
            mutex.withLock {
                val fg = foregroundSender.get()
                if (fg != null) {
                    app.voiceEventBus.emit(VoiceEvent.Status("正在问助手…"))
                    fg.invoke(text)
                    return@withLock
                }
                runBackgroundTurn(text)
            }
        }
    }

    private suspend fun runBackgroundTurn(text: String) {
        val agents = app.agentStore.agents.first()
        val currentId = app.agentStore.currentId.first()
        val agent = agents.find { it.id == currentId } ?: agents.firstOrNull()
        if (agent == null) {
            app.voiceEventBus.emit(VoiceEvent.Error("还没有配置 Agent"))
            return
        }

        app.voiceEventBus.emit(VoiceEvent.Status("正在问 ${agent.name}…"))
        val userId = UUID.randomUUID().toString()
        val assistantId = UUID.randomUUID().toString()
        app.messageRepository.save(
            Message(
                id = userId,
                role = MessageRole.USER,
                content = text,
                providerLabel = agent.kind.label,
                createdAt = System.currentTimeMillis(),
            ),
        )

        val client = AIClientFactory.create(agent, app)
        try {
            withContext(Dispatchers.IO) {
                client.ensureConnected()
                val buffer = StringBuilder()
                client.streamChat(text).collect { buffer.append(it) }
                val raw = buffer.toString()
                val (displayText, _) = ToolCallParser.extract(raw)
                val finalText = displayText.ifBlank { "（空回复）" }
                app.messageRepository.save(
                    Message(
                        id = assistantId,
                        role = MessageRole.ASSISTANT,
                        content = finalText,
                        providerLabel = agent.kind.label,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                // 顶栏只留短状态，完整回复在气泡/通知里，避免占聊天区
                app.voiceEventBus.emit(VoiceEvent.Status("助手已回复"))
                notifyReply(agent.name, finalText)
            }
        } catch (e: Exception) {
            app.voiceEventBus.emit(VoiceEvent.Error(e.message ?: "提问失败"))
            notifyReply(agent.name, "失败：${e.message ?: "未知错误"}")
        } finally {
            client.close()
        }
    }

    private fun notifyReply(agentName: String, body: String) {
        ensureReplyChannel()
        val launch = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, REPLY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(agentName)
            .setContentText(body.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(500)))
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(REPLY_NOTIFICATION_ID, notification)
    }

    private fun ensureReplyChannel() {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                REPLY_CHANNEL_ID,
                "语音助手回复",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    companion object {
        const val REPLY_CHANNEL_ID = "hermchat_voice_reply"
        const val REPLY_NOTIFICATION_ID = 43
    }
}
