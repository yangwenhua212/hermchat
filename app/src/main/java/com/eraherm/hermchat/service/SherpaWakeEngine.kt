package com.eraherm.hermchat.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Offline wake (KWS) → short command ASR → [VoiceEvent.Transcript].
 * Completes: 喊一声 → 说指令 → 执行 (via ChatScreen autoSend).
 */
class SherpaWakeEngine(
    private val context: Context,
    private val kwsModelDir: File,
    private val asrModelDir: File?,
    private val phraseProvider: () -> String,
    private val autoSendProvider: () -> Boolean,
    private val bus: VoiceEventBus,
) : VoiceEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val mode = AtomicReference(Mode.WAKE)
    private var kws: KeywordSpotter? = null
    private var asr: OnlineRecognizer? = null
    private var kwsStream: OnlineStream? = null
    private var asrStream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private var commandStartedAt = 0L
    private var pushToTalkOnly = false

    override fun startListeningLoop() {
        mainHandler.post {
            pushToTalkOnly = false
            if (running.get()) return@post
            if (!prepareKws()) return@post
            prepareAsrQuietly()
            if (!startMic()) return@post
            running.set(true)
            mode.set(Mode.WAKE)
            bus.emit(VoiceEvent.Status("正在听「${phraseProvider()}」"))
            worker = thread(name = "sherpa-voice", start = true) { processLoop() }
        }
    }

    override fun startPushToTalk() {
        mainHandler.post {
            if (asrModelDir == null || !prepareAsr()) {
                bus.emit(VoiceEvent.WakeDetected(phraseProvider()))
                return@post
            }
            if (!running.get()) {
                pushToTalkOnly = true
                if (!startMic()) return@post
                running.set(true)
                worker = thread(name = "sherpa-ptt", start = true) { processLoop() }
            }
            enterCommandMode(fromPushToTalk = true)
        }
    }

    override fun stop() {
        running.set(false)
        pushToTalkOnly = false
        worker = null
        mainHandler.post {
            releaseMic()
            releaseKwsStream()
            releaseAsrStream()
            kws?.release()
            kws = null
            asr?.release()
            asr = null
        }
    }

    private fun prepareKws(): Boolean {
        return try {
            if (kws == null) {
                kws = KeywordSpotter(
                    assetManager = null,
                    config = KeywordSpotterConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = OnlineModelConfig(
                            transducer = OnlineTransducerModelConfig(
                                encoder = File(kwsModelDir, "encoder-epoch-12-avg-2-chunk-16-left-64.onnx").absolutePath,
                                decoder = File(kwsModelDir, "decoder-epoch-12-avg-2-chunk-16-left-64.onnx").absolutePath,
                                joiner = File(kwsModelDir, "joiner-epoch-12-avg-2-chunk-16-left-64.onnx").absolutePath,
                            ),
                            tokens = File(kwsModelDir, "tokens.txt").absolutePath,
                            modelType = "zipformer2",
                        ),
                        keywordsFile = File(kwsModelDir, "keywords.txt").absolutePath,
                        keywordsScore = 1.5f,
                        keywordsThreshold = 0.25f,
                    ),
                )
            }
            recreateKwsStream()
            true
        } catch (t: Throwable) {
            bus.emit(VoiceEvent.Error(t.message ?: "离线引擎启动失败"))
            false
        }
    }

    private fun prepareAsrQuietly() {
        runCatching { prepareAsr() }
    }

    private fun prepareAsr(): Boolean {
        val dir = asrModelDir ?: return false
        return try {
            if (asr == null) {
                asr = OnlineRecognizer(
                    assetManager = null,
                    config = OnlineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = OnlineModelConfig(
                            transducer = OnlineTransducerModelConfig(
                                encoder = File(dir, "encoder-epoch-99-avg-1.int8.onnx").absolutePath,
                                decoder = File(dir, "decoder-epoch-99-avg-1.onnx").absolutePath,
                                joiner = File(dir, "joiner-epoch-99-avg-1.int8.onnx").absolutePath,
                            ),
                            tokens = File(dir, "tokens.txt").absolutePath,
                            modelType = "zipformer",
                            numThreads = 2,
                        ),
                        endpointConfig = EndpointConfig(
                            rule1 = EndpointRule(false, 2.0f, 0.0f),
                            rule2 = EndpointRule(true, 0.8f, 0.0f),
                            rule3 = EndpointRule(false, 0.0f, 8.0f),
                        ),
                        enableEndpoint = true,
                        decodingMethod = "greedy_search",
                    ),
                )
            }
            true
        } catch (t: Throwable) {
            bus.emit(VoiceEvent.Error(t.message ?: "指令识别模型失败"))
            false
        }
    }

    private fun recreateKwsStream(): Boolean {
        releaseKwsStream()
        val spotter = kws ?: return false
        val line = SherpaKeywordCodec.toKeywordLine(phraseProvider())
        val created = spotter.createStream(line)
        if (created.ptr == 0L) {
            bus.emit(VoiceEvent.Error("唤醒词无效"))
            return false
        }
        kwsStream = created
        return true
    }

    private fun recreateAsrStream() {
        releaseAsrStream()
        asrStream = asr?.createStream()
    }

    private fun enterCommandMode(fromPushToTalk: Boolean) {
        if (!prepareAsr()) {
            if (fromPushToTalk) bus.emit(VoiceEvent.WakeDetected(phraseProvider()))
            return
        }
        recreateAsrStream()
        commandStartedAt = System.currentTimeMillis()
        mode.set(Mode.COMMAND)
        mainHandler.post {
            if (!fromPushToTalk) {
                bus.emit(VoiceEvent.WakeDetected(phraseProvider()))
            }
            bus.emit(VoiceEvent.Status("请说指令"))
        }
    }

    private fun returnToWake() {
        if (pushToTalkOnly) {
            running.set(false)
            releaseMic()
            releaseAsrStream()
            mode.set(Mode.WAKE)
            return
        }
        recreateKwsStream()
        releaseAsrStream()
        mode.set(Mode.WAKE)
        mainHandler.post {
            bus.emit(VoiceEvent.Status("正在听「${phraseProvider()}」"))
        }
    }

    private fun startMic(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            bus.emit(VoiceEvent.Error("没有麦克风权限"))
            return false
        }
        if (audioRecord != null) return true
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) {
            bus.emit(VoiceEvent.Error("无法打开麦克风"))
            return false
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            bus.emit(VoiceEvent.Error("无法打开麦克风"))
            return false
        }
        audioRecord = record
        record.startRecording()
        return true
    }

    private fun processLoop() {
        val buffer = ShortArray((SAMPLE_RATE * 0.1).toInt())
        while (running.get()) {
            val record = audioRecord ?: break
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) continue
            val samples = FloatArray(read) { buffer[it] / 32768.0f }
            when (mode.get()) {
                Mode.WAKE -> processWake(samples)
                Mode.COMMAND -> processCommand(samples)
            }
        }
        if (pushToTalkOnly) {
            releaseMic()
            releaseAsrStream()
        }
    }

    private fun processWake(samples: FloatArray) {
        val spotter = kws ?: return
        val current = kwsStream ?: return
        current.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
        while (spotter.isReady(current)) {
            spotter.decode(current)
            val keyword = spotter.getResult(current).keyword
            if (keyword.isNotBlank()) {
                spotter.reset(current)
                enterCommandMode(fromPushToTalk = false)
                return
            }
        }
    }

    private fun processCommand(samples: FloatArray) {
        val recognizer = asr ?: run {
            returnToWake()
            return
        }
        val current = asrStream ?: run {
            returnToWake()
            return
        }
        current.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
        while (recognizer.isReady(current)) {
            recognizer.decode(current)
        }
        val text = recognizer.getResult(current).text.trim()
        val timedOut = System.currentTimeMillis() - commandStartedAt > COMMAND_TIMEOUT_MS
        val endpoint = recognizer.isEndpoint(current)
        if (!endpoint && !timedOut) return

        // Flush a bit of silence so trailing words settle.
        if (endpoint || timedOut) {
            val pad = FloatArray(SAMPLE_RATE / 5)
            current.acceptWaveform(pad, sampleRate = SAMPLE_RATE)
            while (recognizer.isReady(current)) {
                recognizer.decode(current)
            }
        }
        val finalText = recognizer.getResult(current).text.trim()
        recognizer.reset(current)

        if (finalText.isNotBlank()) {
            mainHandler.post {
                bus.emit(
                    VoiceEvent.Transcript(
                        text = finalText,
                        autoSend = autoSendProvider() || pushToTalkOnly,
                    ),
                )
            }
        } else if (text.isNotBlank()) {
            mainHandler.post {
                bus.emit(
                    VoiceEvent.Transcript(
                        text = text,
                        autoSend = autoSendProvider() || pushToTalkOnly,
                    ),
                )
            }
        }
        returnToWake()
    }

    private fun releaseMic() {
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
    }

    private fun releaseKwsStream() {
        runCatching { kwsStream?.release() }
        kwsStream = null
    }

    private fun releaseAsrStream() {
        runCatching { asrStream?.release() }
        asrStream = null
    }

    private enum class Mode { WAKE, COMMAND }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val COMMAND_TIMEOUT_MS = 8_000L
    }
}
