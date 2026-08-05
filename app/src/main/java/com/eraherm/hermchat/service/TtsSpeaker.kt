package com.eraherm.hermchat.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 系统 TTS。
 *
 * 要点：
 * - utteranceId 用独立 UUID，勿用 messageId
 * - stop/flush 的旧 onStop 不得清理新 utterance（用 live 集合过滤）
 * - 绑定系统「首选引擎」包名（国产机测听有声、App 无声常见因绑错引擎）
 * - KEY_PARAM_STREAM 必须用 String
 */
class TtsSpeaker(
    context: Context,
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var boundEngine: String? = null
    private val ready = AtomicBoolean(false)
    private val initFinished = AtomicBoolean(false)
    private val pending = MutableStateFlow<PendingSpeak?>(null)
    private var lastError: String? = null
    private var focusRequest: AudioFocusRequest? = null
    private val liveUtterances = ConcurrentHashMap.newKeySet<String>()

    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val speechAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    fun ensureStarted() {
        if (tts != null) return
        createEngine(preferredEnginePackage())
    }

    private fun createEngine(enginePackage: String?) {
        ready.set(false)
        initFinished.set(false)
        runCatching { tts?.shutdown() }
        tts = null
        boundEngine = enginePackage
        tts = if (!enginePackage.isNullOrBlank()) {
            TextToSpeech(appContext, this, enginePackage)
        } else {
            TextToSpeech(appContext, this)
        }
    }

    private fun preferredEnginePackage(): String? {
        return runCatching {
            Settings.Secure.getString(
                appContext.contentResolver,
                "tts_default_synth",
            )?.takeIf { it.isNotBlank() }
        }.getOrNull()
            ?: runCatching {
                // 部分机型
                @Suppress("DEPRECATION")
                Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.TTS_DEFAULT_SYNTH,
                )?.takeIf { it.isNotBlank() }
            }.getOrNull()
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            // 指定引擎失败则回退默认构造
            if (!boundEngine.isNullOrBlank()) {
                lastError = "首选引擎不可用，改用默认"
                createEngine(null)
                return
            }
            lastError = "TTS 引擎初始化失败"
            ready.set(false)
            initFinished.set(true)
            return
        }

        try {
            engine.setAudioAttributes(speechAttrs)
        } catch (_: Exception) {
            runCatching {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
        }

        val lang = pickChineseLocale(engine)
        val setResult = engine.setLanguage(lang)
        if (setResult == TextToSpeech.LANG_MISSING_DATA ||
            setResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            runCatching { engine.setLanguage(Locale.getDefault()) }
        }

        // 不强行换 voice：国产「系统语音引擎」测听用的就是默认声线
        pickChineseVoice(engine)?.let { voice ->
            runCatching { engine.voice = voice }
        }

        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null && liveUtterances.contains(utteranceId)) {
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
        lastError = null
        val queued = pending.value
        pending.value = null
        queued?.let { speakInternal(it.text, it.messageId, flush = true) }
    }

    fun speak(text: String, messageId: String? = null) {
        speakInternal(text, messageId, flush = true)
    }

    fun speakChunk(text: String, messageId: String?, flush: Boolean) {
        speakInternal(text, messageId, flush = flush)
    }

    private fun speakInternal(text: String, messageId: String?, flush: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { speakInternal(text, messageId, flush) }
            return
        }
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
            // 先清 live，再 stop：旧 onStop 因不在 live 中被忽略，不会掐新朗读
            liveUtterances.clear()
            runCatching { engine.stop() }
        }
        requestAudioFocus()
        val utteranceId = UUID.randomUUID().toString()
        liveUtterances.add(utteranceId)
        _speakingMessageId.value = messageId
        lastError = null
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            // 必须 String；putInt 在部分机型会导致无声
            putString(
                TextToSpeech.Engine.KEY_PARAM_STREAM,
                AudioManager.STREAM_MUSIC.toString(),
            )
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(cleaned, mode, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            liveUtterances.remove(utteranceId)
            lastError = "TTS speak 失败 ($result)"
            _speaking.value = false
            if (liveUtterances.isEmpty()) {
                _speakingMessageId.value = null
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
        liveUtterances.clear()
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
        if (!liveUtterances.remove(utteranceId)) {
            // 过期 stop/done（flush 清队列后），忽略
            return
        }
        if (error != null) lastError = error
        if (liveUtterances.isEmpty()) {
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
            // 勿剥中文标点；只清 markdown 噪声
            t = t.replace(Regex("""[*#>`]"""), " ")
            t = t.replace(Regex("""\s+"""), " ").trim()
            if (t.length > 3500) t = t.take(3500)
            return t
        }
    }

    private data class PendingSpeak(val text: String, val messageId: String?)
}
