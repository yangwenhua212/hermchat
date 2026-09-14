package com.eraherm.hermchat.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Preset-phrase wake + command ASR via on-device / system SpeechRecognizer.
 */
class SpeechWakeEngine(
    private val context: Context,
    private val phraseProvider: () -> String,
    private val autoSendProvider: () -> Boolean,
    private val bus: VoiceEventBus,
) : VoiceEngine {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var mode: Mode = Mode.WAKE
    private var running = false
    private var pushToTalk = false
    private var inAppDirect = false

    override fun startListeningLoop() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                bus.emit(VoiceEvent.Error("本机暂无语音识别，请用键盘输入"))
                return@post
            }
            running = true
            pushToTalk = false
            mode = if (inAppDirect) Mode.COMMAND else Mode.WAKE
            ensureRecognizer()
            VoiceGate.addListener(gateListener)
            restartListening(statusForCurrentMode())
        }
    }

    override fun stop() {
        mainHandler.post {
            running = false
            pushToTalk = false
            VoiceGate.removeListener(gateListener)
            recognizer?.setRecognitionListener(null)
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }

    /** 聊天页点麦克风：不喊唤醒词，直接说指令。 */
    override fun startPushToTalk() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                bus.emit(VoiceEvent.Error("本机暂无语音识别，请用键盘输入"))
                return@post
            }
            pushToTalk = true
            mode = Mode.COMMAND
            ensureRecognizer()
            VoiceGate.addListener(gateListener)
            runCatching { recognizer?.cancel() }
            restartListening("请说指令…")
        }
    }

    override fun setInAppDirectListen(enabled: Boolean) {
        mainHandler.post {
            inAppDirect = enabled
            if (!running || pushToTalk) return@post
            mode = if (enabled) Mode.COMMAND else Mode.WAKE
            runCatching { recognizer?.cancel() }
            restartListening(statusForCurrentMode())
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
        }
    }

    /**
     * 朗读门控：TTS 一开口就停麦，读完冷却后再听。
     * 不这么做的话，监听循环会把刚念出去的回复当指令收回来 —— 自己回答自己（老大实测踩过）。
     */
    private val gateListener = object : VoiceGate.Listener {
        override fun onSpeakStart() {
            // 立刻 cancel：等这一轮识别自然结束的话，自己的声音已经录进去了
            runCatching { recognizer?.cancel() }
        }

        override fun onSpeakEnd() {
            val delay = VoiceGate.cooldownRemainingMs() + 150
            mainHandler.postDelayed({
                when {
                    running -> restartListening(statusForCurrentMode())
                    pushToTalk -> restartListening("请说指令…")
                }
            }, delay)
        }
    }

    private fun restartListening(status: String) {
        if (!running && !pushToTalk) return
        if (!VoiceGate.canListen()) {
            // 正在朗读 / 刚读完：先别开麦，等冷却过去再试（别把喇叭的声音当指令）
            mainHandler.postDelayed({
                if (running || pushToTalk) restartListening(status)
            }, VoiceGate.cooldownRemainingMs().coerceAtLeast(200L))
            return
        }
        bus.emit(VoiceEvent.Status(status))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Prefer on-device when the OEM supports it.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching {
            recognizer?.startListening(intent)
        }.onFailure {
            bus.emit(
                VoiceEvent.Error(
                    com.eraherm.hermchat.util.UserFacingError.of(it, "无法启动语音识别"),
                ),
            )
            scheduleRestart(800)
        }
    }

    private fun statusForCurrentMode(): String =
        if (inAppDirect || mode == Mode.COMMAND) {
            "请说指令…"
        } else {
            "正在听唤醒词「${phraseProvider()}」"
        }

    private fun scheduleRestart(delayMs: Long) {
        if (!running || pushToTalk) return
        mainHandler.postDelayed({
            if (running && !pushToTalk) {
                mode = if (inAppDirect) Mode.COMMAND else Mode.WAKE
                restartListening(statusForCurrentMode())
            }
        }, delayMs)
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(" ", "")
            .replace("，", "")
            .replace(",", "")
            .replace("。", "")
            .replace(".", "")

    private fun containsWake(text: String): Boolean {
        val phrase = normalize(phraseProvider())
        if (phrase.isBlank()) return false
        return normalize(text).contains(phrase)
    }

    private fun stripWake(text: String): String {
        val phrase = phraseProvider()
        var result = text
        listOf(phrase, phrase.lowercase(Locale.ROOT)).forEach { p ->
            result = result.replace(p, "", ignoreCase = true)
        }
        return result.trim().trim(',', '，', '。', '.', ' ', '　')
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            if (pushToTalk) {
                pushToTalk = false
                if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    bus.emit(VoiceEvent.Error("语音识别错误($error)"))
                }
                if (running) {
                    scheduleRestart(400)
                }
                return
            }
            // Soft errors are normal in continuous wake loops.
            scheduleRestart(500)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: return
            handleHypotheses(texts, isFinal = false)
        }

        override fun onResults(results: Bundle?) {
            val texts = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: emptyList()
            handleHypotheses(texts, isFinal = true)
            when {
                pushToTalk -> {
                    pushToTalk = false
                    if (running) {
                        scheduleRestart(400)
                    }
                }
                mode == Mode.COMMAND -> scheduleRestart(400)
                else -> scheduleRestart(300)
            }
        }
    }

    private fun handleHypotheses(texts: List<String>, isFinal: Boolean) {
        if (texts.isEmpty()) return
        val best = texts.first()
        // 麦克风听到的是我们自己念出去的内容 → 不是指令，丢掉（自我回环的出口）
        if (VoiceGate.speaking || VoiceGate.isEcho(best)) return

        when (mode) {
            Mode.WAKE -> {
                if (!containsWake(best)) return
                bus.emit(VoiceEvent.WakeDetected(phraseProvider()))
                val remainder = stripWake(best)
                if (remainder.isNotBlank() && isFinal) {
                    bus.emit(
                        VoiceEvent.Transcript(
                            text = remainder,
                            autoSend = autoSendProvider(),
                        ),
                    )
                    mode = Mode.WAKE
                } else {
                    mode = Mode.COMMAND
                    bus.emit(VoiceEvent.Status("在呢，请说指令…"))
                    mainHandler.postDelayed({
                        if (running || pushToTalk) {
                            restartListening("在呢，请说指令…")
                        }
                    }, 200)
                }
            }

            Mode.COMMAND -> {
                if (!isFinal) return
                val text = if (containsWake(best)) stripWake(best) else best.trim()
                if (text.isBlank()) return
                bus.emit(
                    VoiceEvent.Transcript(
                        text = text,
                        autoSend = autoSendProvider() || pushToTalk,
                    ),
                )
            }
        }
    }

    private enum class Mode { WAKE, COMMAND }
}
