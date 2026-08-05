package com.eraherm.hermchat.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 系统 TTS。
 * utteranceId 必须用独立 UUID，禁止复用 messageId（否则 stop 回调会掐掉新开的朗读）。
 */
class TtsSpeaker(
    context: Context,
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val initFinished = AtomicBoolean(false)
    private val pending = MutableStateFlow<PendingSpeak?>(null)
    private var lastError: String? = null
    private var focusRequest: AudioFocusRequest? = null
    private val activeUtteranceId = AtomicReference<String?>(null)
    private val queuedUtterances = AtomicInteger(0)

    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val speechAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    fun ensureStarted() {
        if (tts != null) return
        // 用默认引擎；系统设置测听有声而 App 无声时，多半是 utterance/焦点问题而非引擎缺失
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            lastError = "TTS 引擎初始化失败"
            ready.set(false)
            initFinished.set(true)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.setAudioAttributes(speechAttrs)
            }
        } catch (_: Exception) {
            // 旧机型忽略
        }

        val lang = pickChineseLocale(engine)
        val setResult = engine.setLanguage(lang)
        if (setResult == TextToSpeech.LANG_MISSING_DATA ||
            setResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            // 仍标记 ready 再试一次默认语言：部分国产机 isLanguageAvailable 误报
            runCatching { engine.setLanguage(Locale.getDefault()) }
            lastError = "中文语音包可能未就绪，将尝试系统默认语音"
        }

        pickChineseVoice(engine)?.let { voice ->
            runCatching { engine.voice = voice }
        }

        engine.setSpeechRate(1.05f)
        engine.setPitch(1.0f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null) {
                    _speaking.value = true
                }
            }

            override fun onDone(utteranceId: String?) {
                finishUtterance(utteranceId, error = null)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishUtterance(utteranceId, error = "TTS 播放出错")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finishUtterance(utteranceId, error = "TTS 播放出错 ($errorCode)")
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                finishUtterance(utteranceId, error = null)
            }
        })
        ready.set(true)
        initFinished.set(true)
        if (lastError?.contains("可能未就绪") != true) {
            lastError = null
        }
        val queued = pending.value
        pending.value = null
        queued?.let { speakInternal(it.text, it.messageId, flush = true) }
    }

    /** 整段朗读（替换队列）。 */
    fun speak(text: String, messageId: String? = null) {
        speakInternal(text, messageId, flush = true)
    }

    /** 追加一句（流式按句）；首句用 [flush]=true。 */
    fun speakChunk(text: String, messageId: String?, flush: Boolean) {
        speakInternal(text, messageId, flush = flush)
    }

    private fun speakInternal(text: String, messageId: String?, flush: Boolean) {
        ensureStarted()
        val cleaned = prepare(text)
        if (cleaned.isBlank()) {
            lastError = "文字内容为空，无法朗读"
            return
        }
        if (!ready.get()) {
            if (initFinished.get()) {
                lastError = lastError ?: "系统朗读不可用，请检查中文语音包"
            } else {
                pending.value = PendingSpeak(cleaned, messageId)
            }
            return
        }
        val engine = tts ?: return
        if (flush) {
            queuedUtterances.set(0)
            activeUtteranceId.set(null)
            runCatching { engine.stop() }
        }
        requestAudioFocus()
        // 关键 UUID：切勿用 messageId，否则 stop→onStop 会误清刚发起的同一条朗读
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId.set(utteranceId)
        _speakingMessageId.value = messageId
        lastError = null
        queuedUtterances.incrementAndGet()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(cleaned, mode, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            lastError = "TTS speak 失败 ($result)"
            queuedUtterances.updateAndGet { (it - 1).coerceAtLeast(0) }
            _speaking.value = false
            if (queuedUtterances.get() == 0) {
                _speakingMessageId.value = null
                activeUtteranceId.compareAndSet(utteranceId, null)
                abandonAudioFocus()
            }
        } else {
            _speaking.value = true
        }
    }

    fun toggle(text: String, messageId: String) {
        if (_speaking.value && _speakingMessageId.value == messageId) {
            stop()
        } else {
            speak(text, messageId)
        }
    }

    fun stop() {
        activeUtteranceId.set(null)
        queuedUtterances.set(0)
        runCatching { tts?.stop() }
        _speaking.value = false
        _speakingMessageId.value = null
        pending.value = null
        abandonAudioFocus()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
        initFinished.set(false)
    }

    fun lastErrorMessage(): String? = lastError

    fun isReady(): Boolean = ready.get()

    fun openSystemTtsSettings(): Boolean {
        return runCatching {
            val intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.recoverCatching {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun finishUtterance(utteranceId: String?, error: String?) {
        if (utteranceId == null) return
        // 队列中任一句结束都减计数；不要要求必须等于 active（ADD 时 active 已是后一句）
        val left = queuedUtterances.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (error != null) lastError = error
        if (left == 0) {
            activeUtteranceId.compareAndSet(utteranceId, null)
            // 若 active 已是别的 id，仍清 speaking
            if (activeUtteranceId.get() == null || activeUtteranceId.get() == utteranceId) {
                activeUtteranceId.set(null)
            }
            _speaking.value = false
            _speakingMessageId.value = null
            abandonAudioFocus()
        }
    }

    private fun pickChineseLocale(engine: TextToSpeech): Locale {
        return listOf(
            Locale.SIMPLIFIED_CHINESE,
            Locale.CHINA,
            Locale.CHINESE,
            Locale("zh", "CN"),
            Locale.TAIWAN,
            Locale.getDefault(),
        ).firstOrNull {
            engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
        } ?: Locale.getDefault()
    }

    private fun pickChineseVoice(engine: TextToSpeech): Voice? {
        val voices = runCatching { engine.voices }.getOrNull() ?: return null
        return voices
            .asSequence()
            .filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .filter { it.locale.language.equals("zh", ignoreCase = true) }
            .sortedByDescending { it.quality }
            .firstOrNull()
        // 若全是 NOT_INSTALLED，返回 null，沿用 setLanguage 的默认声线（系统测听常走这条）
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val existing = focusRequest
                if (existing != null) {
                    runCatching { audioManager.abandonAudioFocusRequest(existing) }
                    focusRequest = null
                }
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(speechAttrs)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = request
                audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
            focusRequest = null
        }
    }

    companion object {
        fun prepare(raw: String): String {
            var t = raw.trim()
            t = t.replace(Regex("""\{[^{}]*"type"\s*:\s*"tool_call"[^{}]*\}""", RegexOption.DOT_MATCHES_ALL), "")
            t = t.replace(Regex("""\{[^{}]*"name"\s*:\s*"(alarm|calendar)\.[^"]+"[^{}]*\}""", RegexOption.DOT_MATCHES_ALL), "")
            t = t.replace(Regex("`{1,3}[^`]*`{1,3}"), " ")
            t = t.replace(Regex("""[*#_>~\[\]()]"""), " ")
            t = t.replace(Regex("""\s+"""), " ").trim()
            if (t.length > 3500) t = t.take(3500)
            return t
        }
    }

    private data class PendingSpeak(val text: String, val messageId: String?)
}
