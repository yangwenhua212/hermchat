package com.eraherm.hermchat.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.eraherm.hermchat.data.local.AgentStore
import com.eraherm.hermchat.data.local.ChatPrefsStore
import com.eraherm.hermchat.data.local.SpeakEngine
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.util.UserFacingError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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
 * 统一朗读入口；支持流式按句开读（系统 QUEUE_ADD / Edge 句级排队）。
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
    private val rootJob = SupervisorJob()
    private val scope = CoroutineScope(rootJob + Dispatchers.Main.immediate)
    private var remoteJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var lastError: String? = null
    private val remoteUnsupported = ConcurrentHashMap.newKeySet<String>()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    /** 自动朗读已处理过的助手消息（跨页面进出仍保留，避免回聊天又读一遍）。 */
    private val autoHandledMessageIds = ConcurrentHashMap.newKeySet<String>()

    fun noteAutoHandled(messageId: String) {
        if (messageId.isNotBlank()) autoHandledMessageIds.add(messageId)
    }

    fun isAutoHandled(messageId: String): Boolean =
        messageId.isNotBlank() && autoHandledMessageIds.contains(messageId)

    fun clearAutoHandled(messageId: String) {
        autoHandledMessageIds.remove(messageId)
    }

    private val _userErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userErrors: SharedFlow<String> = _userErrors.asSharedFlow()

    // ── 流式按句 ──
    private var streamMessageId: String? = null
    private var streamCursor: Int = 0
    private var streamRaw: String = ""
    private var streamChunkIndex: Int = 0
    private val streamEdgeChannel = Channel<String>(Channel.UNLIMITED)
    private var streamEdgeJob: Job? = null

    init {
        scope.launch {
            local.speakingMessageId.collect { id ->
                if (mediaPlayer == null && streamEdgeJob?.isActive != true) {
                    _speakingMessageId.value = id
                }
            }
        }
    }

    fun speak(text: String, messageId: String? = null) {
        endStreamInternal(flush = false)
        lastError = null
        when (chatPrefsStore.prefsFlow.value.speakEngine) {
            SpeakEngine.SYSTEM -> {
                stopRemote()
                speakLocal(text, messageId, reportError = true, flush = true)
            }
            SpeakEngine.EDGE -> {
                remoteJob?.cancel()
                remoteJob = scope.launch {
                    speakEdge(text, messageId, fallbackLocal = true)
                }
            }
            SpeakEngine.REMOTE -> {
                remoteJob?.cancel()
                remoteJob = scope.launch {
                    speakCustomOrAgent(text, messageId, fallbackLocal = true)
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

    /** 开始流式朗读（新助手气泡）。 */
    fun beginStreamSpeak(messageId: String) {
        stop()
        streamMessageId = messageId
        streamCursor = 0
        streamRaw = ""
        streamChunkIndex = 0
        _speakingMessageId.value = messageId
        val engine = chatPrefsStore.prefsFlow.value.speakEngine
        if (engine == SpeakEngine.EDGE ||
            engine == SpeakEngine.REMOTE ||
            engine == SpeakEngine.AUTO
        ) {
            startEdgePump(messageId)
        }
    }

    /** 流式正文更新（传当前全文）。 */
    fun onStreamText(messageId: String, fullText: String) {
        if (messageId != streamMessageId) return
        streamRaw = fullText
        pumpStream(forceFlush = false)
    }

    /** 流式结束：读完尾巴。 */
    fun endStreamSpeak(messageId: String) {
        if (messageId != streamMessageId) return
        pumpStream(forceFlush = true)
        streamMessageId = null
    }

    fun toggle(text: String, messageId: String) {
        if (_speakingMessageId.value == messageId && (local.speaking.value || mediaPlayer != null)) {
            stop()
        } else {
            speak(text, messageId)
        }
    }

    fun stop() {
        endStreamInternal(flush = false)
        stopRemote()
        local.stop()
        _speakingMessageId.value = null
    }

    /** 进程退出前可调用；平时用 [stop] 即可。 */
    fun shutdown() {
        stop()
        rootJob.cancel()
    }

    fun lastErrorMessage(): String? = lastError ?: local.lastErrorMessage()

    fun openSystemTtsSettings(): Boolean = local.openSystemTtsSettings()

    private fun endStreamInternal(flush: Boolean) {
        if (flush && streamMessageId != null) {
            pumpStream(forceFlush = true)
        }
        streamMessageId = null
        streamCursor = 0
        streamRaw = ""
        streamChunkIndex = 0
        streamEdgeJob?.cancel()
        streamEdgeJob = null
        while (streamEdgeChannel.tryReceive().isSuccess) Unit
    }

    private fun pumpStream(forceFlush: Boolean) {
        val messageId = streamMessageId ?: return
        val (sentences, next) = SentenceSplitter.takeNew(
            fullText = streamRaw,
            fromIndex = streamCursor,
            forceFlush = forceFlush,
        )
        streamCursor = next
        if (sentences.isEmpty()) return
        val engine = chatPrefsStore.prefsFlow.value.speakEngine
        for (sentence in sentences) {
            val cleaned = TtsSpeaker.prepare(sentence)
            if (cleaned.isBlank()) continue
            when (engine) {
                SpeakEngine.SYSTEM -> {
                    val flush = streamChunkIndex == 0
                    speakLocal(cleaned, messageId, reportError = streamChunkIndex == 0, flush = flush)
                    streamChunkIndex++
                }
                SpeakEngine.EDGE, SpeakEngine.REMOTE, SpeakEngine.AUTO -> {
                    streamEdgeChannel.trySend(cleaned)
                    streamChunkIndex++
                }
            }
        }
    }

    private fun startEdgePump(messageId: String) {
        streamEdgeJob?.cancel()
        streamEdgeJob = scope.launch {
            for (sentence in streamEdgeChannel) {
                if (streamMessageId != messageId) break
                val prefs = chatPrefsStore.prefsFlow.value
                when (prefs.speakEngine) {
                    SpeakEngine.SYSTEM -> Unit
                    SpeakEngine.EDGE -> {
                        if (!speakEdge(sentence, messageId, fallbackLocal = true)) {
                            // 已回退系统
                        }
                    }
                    SpeakEngine.REMOTE -> {
                        speakCustomOrAgent(sentence, messageId, fallbackLocal = true)
                    }
                    SpeakEngine.AUTO -> {
                        speakAuto(sentence, messageId)
                    }
                }
            }
        }
    }

    private fun speakLocal(
        text: String,
        messageId: String?,
        reportError: Boolean,
        flush: Boolean,
    ) {
        local.speakChunk(text, messageId, flush = flush)
        // speak 是同步入队的；失败立刻有 lastError
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
        speakLocal(text, messageId, reportError = true, flush = true)
    }

    private suspend fun speakEdge(
        text: String,
        messageId: String?,
        fallbackLocal: Boolean,
    ): Boolean {
        stopRemotePlayer()
        // 流式句级：不要 local.stop() 清掉系统回退队列
        val voice = chatPrefsStore.prefsFlow.value.ttsVoice
            .ifBlank { EdgeTtsClient.DEFAULT_VOICE }
        val dest = File(appContext.cacheDir, "tts_edge_${System.nanoTime()}.mp3")
        _speakingMessageId.value = messageId
        val result = withContext(Dispatchers.IO) {
            edge.synthesizeToFile(text = text, dest = dest, voice = voice)
        }
        return result.fold(
            onSuccess = { file ->
                lastError = null
                playFileAwait(file, messageId)
                true
            },
            onFailure = { err ->
                stopRemotePlayer()
                if (fallbackLocal) {
                    lastError = null
                    speakLocal(text, messageId, reportError = true, flush = true)
                } else if (chatPrefsStore.prefsFlow.value.speakEngine == SpeakEngine.AUTO) {
                    lastError = null
                } else {
                    _speakingMessageId.value = null
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
        stopRemotePlayer()
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
                speakLocal(text, messageId, reportError = true, flush = true)
            } else {
                emitError("自定义 TTS 失败，请检查地址与 Key")
            }
            return
        }

        val agent = currentAgent()
        if (agent == null) {
            if (fallbackLocal) speakLocal(text, messageId, reportError = true, flush = true)
            else emitError("请选 Edge 小艺，或填写自定义 TTS 地址")
            return
        }
        if (agent.kind == AgentKind.LOCAL ||
            agent.kind == AgentKind.WEBSOCKET ||
            remoteUnsupported.contains(agent.id)
        ) {
            if (fallbackLocal) speakLocal(text, messageId, reportError = true, flush = true)
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
            if (fallbackLocal) speakLocal(text, messageId, reportError = true, flush = true)
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
            if (fallbackLocal) speakLocal(text, messageId, reportError = true, flush = true)
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
        val dest = File(appContext.cacheDir, "tts_reply_${System.nanoTime()}.mp3")
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
                playFileAwait(file, messageId)
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

    private suspend fun playFileAwait(file: File, messageId: String?) {
        val done = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
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
                        // 句级队列可能还有下一句
                    }
                    done.complete(Unit)
                }
                player.setOnErrorListener { _, _, _ ->
                    emitError("音频播放失败")
                    stopRemotePlayer()
                    done.complete(Unit)
                    true
                }
                player.prepare()
                player.start()
                _speakingMessageId.value = messageId
            } catch (e: Exception) {
                emitError(UserFacingError.of(e, "音频播放失败"))
                stopRemotePlayer()
                done.complete(Unit)
            }
        }
        done.await()
        runCatching { file.delete() }
    }

    private fun stopRemote() {
        remoteJob?.cancel()
        remoteJob = null
        streamEdgeJob?.cancel()
        streamEdgeJob = null
        while (streamEdgeChannel.tryReceive().isSuccess) Unit
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
