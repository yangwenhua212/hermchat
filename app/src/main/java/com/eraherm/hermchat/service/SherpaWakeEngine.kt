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
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Offline keyword spotting via sherpa-onnx.
 * Wake only for now; push-to-talk emits [VoiceEvent.WakeDetected] so UI can take typed input.
 */
class SherpaWakeEngine(
    private val context: Context,
    private val modelDir: File,
    private val phraseProvider: () -> String,
    private val bus: VoiceEventBus,
) : VoiceEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var kws: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null

    override fun startListeningLoop() {
        mainHandler.post {
            if (running.get()) return@post
            if (!prepareSpotter()) return@post
            if (!startMic()) return@post
            running.set(true)
            bus.emit(VoiceEvent.Status("正在听「${phraseProvider()}」"))
            worker = thread(name = "sherpa-kws", start = true) { processLoop() }
        }
    }

    override fun startPushToTalk() {
        // No offline ASR yet — nudge the chat UI into ready-to-type state.
        bus.emit(VoiceEvent.WakeDetected(phraseProvider()))
    }

    override fun stop() {
        running.set(false)
        worker = null
        mainHandler.post {
            releaseMic()
            releaseStream()
            kws?.release()
            kws = null
        }
    }

    private fun prepareSpotter(): Boolean {
        return try {
            if (kws == null) {
                val encoder = File(modelDir, "encoder-epoch-12-avg-2-chunk-16-left-64.onnx").absolutePath
                val decoder = File(modelDir, "decoder-epoch-12-avg-2-chunk-16-left-64.onnx").absolutePath
                val joiner = File(modelDir, "joiner-epoch-12-avg-2-chunk-16-left-64.onnx").absolutePath
                val tokens = File(modelDir, "tokens.txt").absolutePath
                val keywords = File(modelDir, "keywords.txt").absolutePath
                kws = KeywordSpotter(
                    assetManager = null,
                    config = KeywordSpotterConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = OnlineModelConfig(
                            transducer = OnlineTransducerModelConfig(
                                encoder = encoder,
                                decoder = decoder,
                                joiner = joiner,
                            ),
                            tokens = tokens,
                            modelType = "zipformer2",
                        ),
                        keywordsFile = keywords,
                        keywordsScore = 1.5f,
                        keywordsThreshold = 0.25f,
                    ),
                )
            }
            releaseStream()
            val line = SherpaKeywordCodec.toKeywordLine(phraseProvider())
            val created = kws!!.createStream(line)
            if (created.ptr == 0L) {
                bus.emit(VoiceEvent.Error("唤醒词无效"))
                return false
            }
            stream = created
            true
        } catch (t: Throwable) {
            bus.emit(VoiceEvent.Error(t.message ?: "离线引擎启动失败"))
            false
        }
    }

    private fun startMic(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            bus.emit(VoiceEvent.Error("没有麦克风权限"))
            return false
        }
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
        val spotter = kws ?: return
        while (running.get()) {
            val record = audioRecord ?: break
            val current = stream ?: break
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) continue
            val samples = FloatArray(read) { buffer[it] / 32768.0f }
            current.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
            while (spotter.isReady(current)) {
                spotter.decode(current)
                val keyword = spotter.getResult(current).keyword
                if (keyword.isNotBlank()) {
                    spotter.reset(current)
                    mainHandler.post {
                        bus.emit(VoiceEvent.WakeDetected(phraseProvider()))
                        bus.emit(VoiceEvent.Status("在呢"))
                    }
                }
            }
        }
        releaseMic()
        releaseStream()
    }

    private fun releaseMic() {
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
    }

    private fun releaseStream() {
        runCatching { stream?.release() }
        stream = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
    }
}
