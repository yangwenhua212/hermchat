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
import java.util.concurrent.atomic.AtomicReference

/**
 * 系统 TTS 朗读。焦点失败仍尝试播放；按 utteranceId 释放焦点，避免 stop 竞态掐声。
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
            lastError = "未安装中文语音包，请在系统「文字转语音」中下载"
            ready.set(false)
            initFinished.set(true)
            return
        }

        pickChineseVoice(engine)?.let { voice ->
            runCatching { engine.voice = voice }
        }
        val voice = engine.voice
        if (voice != null &&
            voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        ) {
            lastError = "中文语音包未下载，请在系统设置中安装"
            ready.set(false)
            initFinished.set(true)
            return
        }

        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null && utteranceId == activeUtteranceId.get()) {
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
                // 仅清理「当前」这句，避免 stop() 后新 speak 的焦点被误释放
                if (utteranceId != null && utteranceId == activeUtteranceId.get()) {
                    finishUtterance(utteranceId, error = null)
                }
            }
        })
        ready.set(true)
        initFinished.set(true)
        lastError = null
        val queued = pending.value
        pending.value = null
        queued?.let { speak(it.text, it.messageId) }
    }

    fun speak(text: String, messageId: String? = null) {
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
        // 只停引擎，不清 pending / 不抢跑 abandon（由旧 utterance 回调忽略）
        runCatching { engine.stop() }
        requestAudioFocus() // 失败也继续播，避免国产机 ASSISTANT/焦点硬拦
        val id = messageId ?: UUID.randomUUID().toString()
        activeUtteranceId.set(id)
        _speakingMessageId.value = messageId
        lastError = null
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
            putString(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
        }
        val result = engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result != TextToSpeech.SUCCESS) {
            lastError = "TTS speak 失败 ($result)"
            _speaking.value = false
            _speakingMessageId.value = null
            activeUtteranceId.compareAndSet(id, null)
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
        activeUtteranceId.set(null)
        runCatching { tts?.stop() }
        _speaking.value = false
        _speakingMessageId.value = null
        pending.value = null
        // 主动 stop 时立刻放焦点；过期 onStop 会被 utterance 校验忽略
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
        if (!activeUtteranceId.compareAndSet(utteranceId, null)) return
        if (error != null) lastError = error
        _speaking.value = false
        _speakingMessageId.value = null
        abandonAudioFocus()
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
