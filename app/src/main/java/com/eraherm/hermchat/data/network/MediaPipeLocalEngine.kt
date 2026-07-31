package com.eraherm.hermchat.data.network

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/** Thin wrapper around MediaPipe on-device LLM. */
class MediaPipeLocalEngine(
    private val context: Context,
) {
    private val inferenceRef = AtomicReference<LlmInference?>(null)

    val isLoaded: Boolean get() = inferenceRef.get() != null

    suspend fun ensureLoaded(modelPath: String): Unit = withContext(Dispatchers.Default) {
        if (inferenceRef.get() != null) return@withContext
        synchronized(this@MediaPipeLocalEngine) {
            if (inferenceRef.get() != null) return@synchronized
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            inferenceRef.set(LlmInference.createFromOptions(context, options))
        }
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        val inference = inferenceRef.get() ?: error("模型未加载")
        inference.generateResponse(prompt)
    }

    fun close() {
        inferenceRef.getAndSet(null)?.close()
    }
}
