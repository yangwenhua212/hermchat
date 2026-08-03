package com.eraherm.hermchat.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.eraherm.hermchat.data.local.AgentStore
import com.eraherm.hermchat.data.local.ChatPrefsStore
import com.eraherm.hermchat.data.local.SpeakEngine
import com.eraherm.hermchat.data.model.AgentKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 统一朗读入口：系统 TTS 和/或 Agent 云端 `/v1/audio/speech`。
 */
class ReplySpeaker(
    context: Context,
    private val local: TtsSpeaker,
    private val agentStore: AgentStore,
    private val chatPrefsStore: ChatPrefsStore,
    private val remote: RemoteTtsClient = RemoteTtsClient(),
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var remoteJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var lastError: String? = null

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    init {
        scope.launch {
            local.speakingMessageId.collect { id ->
                if (mediaPlayer == null) {
                    _speakingMessageId.value = id
                }
            }
        }
    }

    fun speak(text: String, messageId: String? = null) {
        val engine = chatPrefsStore.prefsFlow.value.speakEngine
        when (engine) {
            SpeakEngine.SYSTEM -> {
                stopRemote()
                local.speak(text, messageId)
            }
            SpeakEngine.REMOTE -> scope.launch {
                speakRemoteOrFail(text, messageId, fallbackLocal = false)
            }
            SpeakEngine.AUTO -> scope.launch {
                speakRemoteOrFail(text, messageId, fallbackLocal = true)
            }
        }
    }

    fun toggle(text: String, messageId: String) {
        if (_speakingMessageId.value == messageId && (local.speaking.value || mediaPlayer != null)) {
            stop()
        } else {
            speak(text, messageId)
        }
    }

    fun stop() {
        stopRemote()
        local.stop()
        _speakingMessageId.value = null
    }

    fun lastErrorMessage(): String? = lastError ?: local.lastErrorMessage()

    fun openSystemTtsSettings(): Boolean = local.openSystemTtsSettings()

    private suspend fun speakRemoteOrFail(
        text: String,
        messageId: String?,
        fallbackLocal: Boolean,
    ) {
        stopRemote()
        local.stop()
        val agent = currentAgent()
        if (agent == null) {
            if (fallbackLocal) {
                lastError = null
                local.speak(text, messageId)
            } else {
                lastError = "还没有配置 Agent"
            }
            return
        }
        val endpoint = agent.endpoint.trim()
        val canRemote = endpoint.isNotBlank() &&
            (
                agent.kind == AgentKind.HERMES ||
                    agent.kind == AgentKind.HTTP_COMPAT ||
                    agent.kind == AgentKind.GATEWAY ||
                    endpoint.startsWith("http", ignoreCase = true)
                )
        if (!canRemote) {
            if (fallbackLocal) {
                lastError = null
                local.speak(text, messageId)
            } else {
                lastError = "当前 Agent 不支持云端朗读，请改用系统朗读"
            }
            return
        }
        val apiKey = agent.apiKey
        remoteJob = scope.launch {
            _speakingMessageId.value = messageId
            val dest = File(appContext.cacheDir, "tts_reply.mp3")
            val result = withContext(Dispatchers.IO) {
                remote.synthesizeToFile(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    text = text,
                    dest = dest,
                )
            }
            result.fold(
                onSuccess = { file ->
                    lastError = null
                    playFile(file, messageId)
                },
                onFailure = { err ->
                    stopRemotePlayer()
                    _speakingMessageId.value = null
                    if (fallbackLocal) {
                        lastError = null
                        local.speak(text, messageId)
                    } else {
                        lastError = com.eraherm.hermchat.util.UserFacingError.of(err, "云端朗读失败")
                    }
                },
            )
        }
    }

    private fun playFile(file: File, messageId: String?) {
        stopRemotePlayer()
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                stopRemotePlayer()
                if (_speakingMessageId.value == messageId) {
                    _speakingMessageId.value = null
                }
            }
            player.setOnErrorListener { _, _, _ ->
                lastError = "音频播放失败"
                stopRemotePlayer()
                _speakingMessageId.value = null
                true
            }
            player.prepare()
            player.start()
            _speakingMessageId.value = messageId
        } catch (e: Exception) {
            lastError = com.eraherm.hermchat.util.UserFacingError.of(e, "音频播放失败")
            stopRemotePlayer()
            _speakingMessageId.value = null
        }
    }

    private fun stopRemote() {
        remoteJob?.cancel()
        remoteJob = null
        stopRemotePlayer()
    }

    private fun stopRemotePlayer() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    private suspend fun currentAgent() =
        agentStore.agents.first().let { list ->
            val id = agentStore.currentId.first()
            list.find { it.id == id } ?: list.firstOrNull()
        }
}
