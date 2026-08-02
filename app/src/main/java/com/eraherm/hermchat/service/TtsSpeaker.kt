package com.eraherm.hermchat.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 用系统 TTS 朗读助手回复。
 */
class TtsSpeaker(
    context: Context,
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val pending = MutableStateFlow<PendingSpeak?>(null)
    private var lastError: String? = null

    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    fun ensureStarted() {
        if (tts != null) return
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            lastError = "TTS 引擎初始化失败 (code=$status)"
            ready.set(false)
            return
        }

        // 按优先级尝试中文，回退到系统默认
        val lang = listOf(
            Locale.SIMPLIFIED_CHINESE,
            Locale.CHINA,
            Locale.CHINESE,
            Locale.TAIWAN,
            Locale.getDefault(),
        ).firstOrNull {
            engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
        } ?: Locale.getDefault()

        val availability = engine.isLanguageAvailable(lang)
        val setResult = engine.setLanguage(lang)

        if (setResult == TextToSpeech.LANG_MISSING_DATA ||
            setResult == TextToSpeech.LANG_NOT_SUPPORTED ||
            availability == TextToSpeech.LANG_MISSING_DATA
        ) {
            lastError = "未安装中文语音包，请在系统设置 → 文字转语音中下载"
            ready.set(false)
            return
        }

        // 确保语音包已安装（LANG_AVAILABLE 不等于数据已下载）
        val voice = engine.voice
        if (voice != null && voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
            lastError = "语音包未下载，请在系统设置中安装"
            ready.set(false)
            return
        }

        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _speaking.value = false
                _speakingMessageId.value = null
                abandonAudioFocus()
            }

            override fun onError(utteranceId: String?) {
                lastError = "TTS 播放出错"
                _speaking.value = false
                _speakingMessageId.value = null
                abandonAudioFocus()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                lastError = "TTS 播放出错 (code=$errorCode)"
                _speaking.value = false
                _speakingMessageId.value = null
                abandonAudioFocus()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                _speaking.value = false
                _speakingMessageId.value = null
                abandonAudioFocus()
            }
        })
        ready.set(true)
        lastError = null
        pending.value?.let { speak(it.text, it.messageId) }
        pending.value = null
    }

    fun speak(text: String, messageId: String? = null) {
        ensureStarted()
        val cleaned = prepare(text)
        if (cleaned.isBlank()) {
            lastError = "文字内容为空，无法朗读"
            return
        }
        if (!ready.get()) {
            pending.value = PendingSpeak(cleaned, messageId)
            return
        }
        val engine = tts ?: return
        stop()
        if (!requestAudioFocus()) {
            lastError = "无法获取音频焦点"
            return
        }
        _speakingMessageId.value = messageId
        val id = messageId ?: UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }
        val result = engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result != TextToSpeech.SUCCESS) {
            lastError = "TTS speak 失败 (code=$result)"
            _speaking.value = false
            _speakingMessageId.value = null
            abandonAudioFocus()
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
        tts?.stop()
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
    }

    /** 供外部查询最近一次失败原因。 */
    fun lastErrorMessage(): String? = lastError

    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(false)
                    .build()
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
            true // 兼容：有些设备可能抛异常，继续尝试播放
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(null as AudioFocusRequest?)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    companion object {
        /** 去掉工具 JSON / 多余符号，方便朗读。 */
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
