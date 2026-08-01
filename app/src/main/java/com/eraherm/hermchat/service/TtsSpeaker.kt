package com.eraherm.hermchat.service

import android.content.Context
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
 * 用系统 TTS 朗读助手回复（类似豆包「读出来」）。
 */
class TtsSpeaker(
    context: Context,
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val pending = MutableStateFlow<PendingSpeak?>(null)

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
            ready.set(false)
            return
        }
        val lang = listOf(Locale.SIMPLIFIED_CHINESE, Locale.CHINA, Locale.CHINESE, Locale.getDefault())
            .firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
            ?: Locale.getDefault()
        engine.language = lang
        engine.setSpeechRate(1.0f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _speaking.value = false
                _speakingMessageId.value = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _speaking.value = false
                _speakingMessageId.value = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _speaking.value = false
                _speakingMessageId.value = null
            }
        })
        ready.set(true)
        pending.value?.let { speak(it.text, it.messageId) }
        pending.value = null
    }

    fun speak(text: String, messageId: String? = null) {
        ensureStarted()
        val cleaned = prepare(text)
        if (cleaned.isBlank()) return
        if (!ready.get()) {
            pending.value = PendingSpeak(cleaned, messageId)
            return
        }
        val engine = tts ?: return
        stop()
        _speakingMessageId.value = messageId
        val id = messageId ?: UUID.randomUUID().toString()
        val params = Bundle()
        engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, id)
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
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }

    companion object {
        /** 去掉工具 JSON / 多余符号，方便朗读。 */
        fun prepare(raw: String): String {
            var t = raw.trim()
            // 去掉 tool_call JSON 块
            t = t.replace(Regex("""\{[^{}]*"type"\s*:\s*"tool_call"[^{}]*\}""", RegexOption.DOT_MATCHES_ALL), "")
            t = t.replace(Regex("""\{[^{}]*"name"\s*:\s*"(alarm|calendar)\.[^"]+"[^{}]*\}""", RegexOption.DOT_MATCHES_ALL), "")
            t = t.replace(Regex("`{1,3}[^`]*`{1,3}"), " ")
            t = t.replace(Regex("""[*#_>~\[\]()]"""), " ")
            t = t.replace(Regex("""\s+"""), " ").trim()
            // TTS 单段过长部分机型会截断，分段由引擎处理；这里截到合理长度
            if (t.length > 3500) t = t.take(3500)
            return t
        }
    }

    private data class PendingSpeak(val text: String, val messageId: String?)
}
