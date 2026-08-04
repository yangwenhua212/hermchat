package com.eraherm.hermchat.data.network

import android.content.Context
import com.eraherm.hermchat.data.local.DeviceCapability
import com.eraherm.hermchat.data.local.LocalModelStore
import com.eraherm.hermchat.tools.LocalToolPlanner
import com.eraherm.hermchat.tools.LocalToolsPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Phase B local runtime: on-device MediaPipe LLM when model is ready and device RAM allows;
 * otherwise a lightweight local orchestrator (tools still work via ChatViewModel).
 * Model weights are downloaded on demand — never bundled in the APK.
 *
 * 重要：不要在 [ensureConnected] 里预加载权重。MediaPipe 原生 OOM 会直接杀进程，
 * 进聊天/回前台就会闪退；只在真正推理时再加载。
 */
class LocalRuntimeClient(
    private val context: Context,
    private val modelStore: LocalModelStore = LocalModelStore(context),
    private val modelId: String = LocalModelStore.DEFAULT_MODEL_ID,
    private val hfToken: String = "",
    private val engine: MediaPipeLocalEngine = MediaPipeLocalEngine(context.applicationContext),
) : StreamingChatClient {

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private fun approxBytes(): Long = modelStore.expectedBytes(modelId)

    override suspend fun ensureConnected() {
        // 仅标记会话可用；日程/闹钟编排不依赖权重。切勿在此 createFromOptions。
        _connected.value = true
    }

    override fun streamChat(
        prompt: String,
        history: List<ChatTurn>,
        imageDataUrl: String?,
    ): Flow<String> = flow {
        val tool = LocalToolPlanner.plan(prompt)
        if (tool != null) {
            emit(orchestratorToolAck())
            return@flow
        }

        if (!modelStore.isReady(modelId)) {
            emit(orchestratorFallback(prompt, missingModel = true))
            return@flow
        }

        val refuse = DeviceCapability.refuseReason(context, approxBytes())
        if (refuse != null) {
            emit(refuse)
            return@flow
        }

        if (!engine.isLoaded) {
            val loaded = runCatching {
                engine.ensureLoaded(modelStore.modelFile(modelId).absolutePath)
            }
            if (loaded.isFailure) {
                emit(orchestratorFallback(prompt, error = loaded.exceptionOrNull()?.message))
                return@flow
            }
        }

        val text = runCatching {
            engine.generate(buildPrompt(prompt))
        }.getOrElse { err ->
            orchestratorFallback(prompt, err.message)
        }
        emit(text)
    }.flowOn(Dispatchers.Default)

    /**
     * 实验「本地优先解析」：注入压缩工具协议，让小模型试跑 tool JSON。
     * 失败返回 null（未就绪 / 推理异常），由调用方改走云端。
     */
    suspend fun tryGenerateToolPlan(prompt: String): String? = withContext(Dispatchers.Default) {
        if (!modelStore.isReady(modelId)) return@withContext null
        if (!DeviceCapability.canRunLocalLlm(context, approxBytes())) return@withContext null
        val loaded = runCatching {
            if (!engine.isLoaded) {
                engine.ensureLoaded(modelStore.modelFile(modelId).absolutePath)
            }
        }
        if (loaded.isFailure) return@withContext null
        runCatching {
            engine.generate(buildToolPlanPrompt(prompt))
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    override fun close() {
        engine.close()
        _connected.value = false
    }

    private fun buildPrompt(user: String): String = buildString {
        // TinyLlama 等偏英文权重：勿写死「用简体中文」，否则英文提问也会被逼出烂中文/乱码。
        if (isEnglishPrimaryModel(modelId)) {
            appendLine("You are HxSync, a brief on-device phone assistant.")
            appendLine("Reply in the same language as the user. Keep answers short.")
            appendLine("User: $user")
            append("Assistant:")
        } else {
            appendLine("你是手机里的个人助手 HxSync 本地运行时。用简体中文简短回答。")
            appendLine("用户：$user")
            append("助手：")
        }
    }

    private fun buildToolPlanPrompt(user: String): String = buildString {
        appendLine(LocalToolsPrompt.LOCAL_COMPACT)
        appendLine()
        append(LocalToolsPrompt.userPrefix())
        appendLine("用户：$user")
        append("助手：")
    }

    private fun orchestratorToolAck(): String =
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

        fun isEnglishPrimaryModel(modelId: String): Boolean =
            modelId == LocalModelStore.MODEL_TINYLLAMA_ID ||
                modelId.contains("tinyllama", ignoreCase = true)
    }
}
