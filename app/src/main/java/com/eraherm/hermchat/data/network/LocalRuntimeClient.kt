package com.eraherm.hermchat.data.network

import android.content.Context
import com.eraherm.hermchat.data.local.LocalModelStore
import com.eraherm.hermchat.tools.LocalToolPlanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Phase B local runtime: on-device MediaPipe LLM when model is ready;
 * otherwise a lightweight local orchestrator (tools still work via ChatViewModel).
 */
class LocalRuntimeClient(
    context: Context,
    private val modelStore: LocalModelStore = LocalModelStore(context),
    private val modelId: String = LocalModelStore.DEFAULT_MODEL_ID,
    private val hfToken: String = "",
    private val engine: MediaPipeLocalEngine = MediaPipeLocalEngine(context.applicationContext),
) : StreamingChatClient {

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    override suspend fun ensureConnected() {
        if (modelStore.isReady(modelId)) {
            runCatching {
                engine.ensureLoaded(modelStore.modelFile(modelId).absolutePath)
                _connected.value = true
            }.onFailure {
                // Model file present but engine failed (e.g. emulator) — still "connected" for orchestrator.
                _connected.value = true
            }
        } else {
            _connected.value = true
        }
    }

    override fun streamChat(prompt: String): Flow<String> = flow {
        val tool = LocalToolPlanner.plan(prompt)
        if (tool != null) {
            emit(orchestratorToolAck(prompt))
            return@flow
        }

        if (modelStore.isReady(modelId) && engine.isLoaded) {
            val text = runCatching {
                engine.generate(buildPrompt(prompt))
            }.getOrElse { err ->
                orchestratorFallback(prompt, err.message)
            }
            emit(text)
            return@flow
        }

        if (modelStore.isReady(modelId) && !engine.isLoaded) {
            runCatching {
                engine.ensureLoaded(modelStore.modelFile(modelId).absolutePath)
                emit(engine.generate(buildPrompt(prompt)))
                return@flow
            }
        }

        emit(orchestratorFallback(prompt, missingModel = !modelStore.isReady(modelId)))
    }.flowOn(Dispatchers.Default)

    override fun close() {
        engine.close()
        _connected.value = false
    }

    private fun buildPrompt(user: String): String = buildString {
        appendLine("你是手机里的个人助手 HxSync 本地运行时。用简体中文简短回答。")
        appendLine("用户：$user")
        append("助手：")
    }

    private fun orchestratorToolAck(prompt: String): String =
        "好的，已识别到需要操作手机。请在确认卡上允许后继续。"

    private fun orchestratorFallback(
        prompt: String,
        error: String? = null,
        missingModel: Boolean = false,
    ): String {
        val normalized = prompt.trim()
        return when {
            missingModel -> "本地模型未就绪。请在配置里下载模型后再聊，或先用日程/闹钟指令。"
            error != null -> "本地推理暂不可用。日程和闹钟仍可直接说出来用。"
            GREETING.any { normalized.contains(it) } -> "在。我是本地助手，可以帮你设日程和闹钟。"
            else -> "已收到。下载本地模型后可离线问答；日程和闹钟现在就能用。"
        }
    }

    companion object {
        private val GREETING = listOf("你好", "在吗", "嗨", "hello", "hi")
    }
}
