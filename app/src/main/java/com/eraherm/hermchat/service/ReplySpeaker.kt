package com.eraherm.hermchat.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.eraherm.hermchat.data.local.AgentStore
import com.eraherm.hermchat.data.local.ChatPrefsStore
import com.eraherm.hermchat.data.local.SpeakEngine
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.util.UserFacingError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一朗读入口。
 *
 * - [SpeakEngine.EDGE]：微软 Edge TTS（与 Hermes `tts.provider: edge` 同路，默认小艺）
 * - [SpeakEngine.REMOTE]：自填 OpenAI 兼容 TTS 基址
 * - [SpeakEngine.AUTO]：自定义地址 → Edge → 系统
 * - [SpeakEngine.SYSTEM]：手机系统 TTS
 *
 * Hermes 聊天 API 本身通常不提供 `/v1/audio/speech`；Edge 由手机直连微软，不必经 Hermes。
 */
class ReplySpeaker(
    context: Context,
    private val local: TtsSpeaker,
    private val agentStore: AgentStore,
    private val chatPrefsStore: ChatPrefsStore,
    private val remote: RemoteTtsClient = RemoteTtsClient(),
    private val edge: EdgeTtsClient = EdgeTtsClient(),
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var remoteJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var lastError: String? = null
    private val remoteUnsupported = ConcurrentHashMap.newKeySet<String>()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private val _userErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userErrors: SharedFlow<String> = _userErrors.asSharedFlow()

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
        lastError = null
        when (chatPrefsStore.prefsFlow.value.speakEngine) {
            SpeakEngine.SYSTEM -> {
                stopRemote()
                speakLocal(text, messageId, reportError = true)
            }
            SpeakEngine.EDGE -> {
                remoteJob?.cancel()
                remoteJob = scope.launch {
                    speakEdge(text, messageId, fallbackLocal = false)
                }
            }
            SpeakEngine.REMOTE -> {
                remoteJob?.cancel()
                remoteJob = scope.launch {
                    speakCustomOrAgent(text, messageId, fallbackLocal = false)
                }
            }
            SpeakEngine.AUTO -> {
                remoteJob?.cancel()
                remoteJob = scope.launch {
                    speakAuto(text, messageId)
                }
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

    private fun speakLocal(text: String, messageId: String?, reportError: Boolean) {
        local.speak(text, messageId)
        if (reportError) {
            local.lastErrorMessage()?.let { emitError(it) }
        }
    }

    private suspend fun speakAuto(text: String, messageId: String?) {
        val prefs = chatPrefsStore.prefsFlow.value
        if (prefs.ttsEndpoint.isNotBlank()) {
            val ok = synthesizeCustom(
                endpoint = prefs.ttsEndpoint.trim(),
                apiKey = prefs.ttsApiKey,
                model = prefs.ttsModel.ifBlank { "tts-1" },
                voice = prefs.ttsVoice.ifBlank { "alloy" },
                text = text,
                messageId = messageId,
            )
            if (ok) return
        }
        if (speakEdge(text, messageId, fallbackLocal = false)) return
        speakLocal(text, messageId, reportError = true)
    }

    /** @return true 若已成功开播 */
    private suspend fun speakEdge(
        text: String,
        messageId: String?,
        fallbackLocal: Boolean,
    ): Boolean {
        stopRemote()
        local.stop()
        val voice = chatPrefsStore.prefsFlow.value.ttsVoice
            .ifBlank { EdgeTtsClient.DEFAULT_VOICE }
        val dest = File(appContext.cacheDir, "tts_edge.mp3")
        _speakingMessageId.value = messageId
        val result = withContext(Dispatchers.IO) {
            edge.synthesizeToFile(text = text, dest = dest, voice = voice)
        }
        return result.fold(
            onSuccess = { file ->
                lastError = null
                playFile(file, messageId)
                true
            },
            onFailure = { err ->
                stopRemotePlayer()
                _speakingMessageId.value = null
                if (fallbackLocal) {
                    lastError = null
                    speakLocal(text, messageId, reportError = true)
                } else if (chatPrefsStore.prefsFlow.value.speakEngine == SpeakEngine.AUTO) {
                    // AUTO 上层会再回退系统
                    lastError = null
                } else {
                    emitError(UserFacingError.of(err, "Edge 朗读失败"))
                }
                false
            },
        )
    }

    private suspend fun speakCustomOrAgent(
        text: String,
        messageId: String?,
        fallbackLocal: Boolean,
    ) {
        stopRemote()
        local.stop()
        val prefs = chatPrefsStore.prefsFlow.value
        val customEndpoint = prefs.ttsEndpoint.trim()
        if (customEndpoint.isNotBlank()) {
            val ok = synthesizeCustom(
                endpoint = customEndpoint,
                apiKey = prefs.ttsApiKey,
                model = prefs.ttsModel.ifBlank { "tts-1" },
                voice = prefs.ttsVoice.ifBlank { "alloy" },
                text = text,
                messageId = messageId,
            )
            if (ok) return
            if (fallbackLocal) {
                speakLocal(text, messageId, reportError = true)
            } else {
                emitError("自定义 TTS 失败，请检查地址与 Key")
            }
            return
        }

        val agent = currentAgent()
        if (agent == null) {
            if (fallbackLocal) speakLocal(text, messageId, reportError = true)
            else emitError("请选 Edge 小艺，或填写自定义 TTS 地址")
            return
        }
        if (agent.kind == AgentKind.LOCAL ||
            agent.kind == AgentKind.WEBSOCKET ||
            remoteUnsupported.contains(agent.id)
        ) {
            if (fallbackLocal) speakLocal(text, messageId, reportError = true)
            else emitError("请选 Edge 小艺（与 Hermes edge 同路），或填写自定义 TTS 地址")
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
            if (fallbackLocal) speakLocal(text, messageId, reportError = true)
            else emitError("请选 Edge 小艺，或填写自定义 TTS 地址")
            return
        }
        val ok = synthesizeCustom(
            endpoint = endpoint,
            apiKey = agent.apiKey,
            model = prefs.ttsModel.ifBlank { "tts-1" },
            voice = prefs.ttsVoice.ifBlank { "alloy" },
            text = text,
            messageId = messageId,
            markUnsupportedKey = agent.id,
        )
        if (!ok) {
            if (fallbackLocal) speakLocal(text, messageId, reportError = true)
            else emitError("聊天地址没有朗读接口。Hermes 的 Edge 请在设置里选「Edge 小艺」")
        }
    }

    private suspend fun synthesizeCustom(
        endpoint: String,
        apiKey: String,
        model: String,
        voice: String,
        text: String,
        messageId: String?,
        markUnsupportedKey: String? = null,
    ): Boolean {
        _speakingMessageId.value = messageId
        val dest = File(appContext.cacheDir, "tts_reply.mp3")
        val result = withContext(Dispatchers.IO) {
            remote.synthesizeToFile(
                endpoint = endpoint,
                apiKey = apiKey,
                text = text,
                dest = dest,
                model = model,
                voice = voice,
            )
        }
        return result.fold(
            onSuccess = { file ->
                lastError = null
                playFile(file, messageId)
                true
            },
            onFailure = { err ->
                stopRemotePlayer()
                _speakingMessageId.value = null
                if (markUnsupportedKey != null && isUnsupportedRemote(err)) {
                    remoteUnsupported.add(markUnsupportedKey)
                }
                false
            },
        )
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
                emitError("音频播放失败")
                stopRemotePlayer()
                _speakingMessageId.value = null
                true
            }
            player.prepare()
            player.start()
            _speakingMessageId.value = messageId
        } catch (e: Exception) {
            emitError(UserFacingError.of(e, "音频播放失败"))
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

    private fun emitError(message: String) {
        lastError = message
        _userErrors.tryEmit(message)
    }

    private fun isUnsupportedRemote(err: Throwable): Boolean {
        val msg = err.message.orEmpty()
        return msg.contains("404") ||
            msg.contains("405") ||
            msg.contains("501") ||
            msg.contains("不可用")
    }

    private suspend fun currentAgent() =
        agentStore.agents.first().let { list ->
            val id = agentStore.currentId.first()
            list.find { it.id == id } ?: list.firstOrNull()
        }
}
