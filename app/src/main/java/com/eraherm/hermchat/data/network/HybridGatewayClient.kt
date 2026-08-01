package com.eraherm.hermchat.data.network

import android.content.Context
import com.eraherm.hermchat.data.local.GatewayRouteMode
import com.eraherm.hermchat.data.local.LocalModelStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * ④ 端侧 Agent 网关：本地小模型 + OpenAI 兼容 API 混合路由，本机工具由 ChatViewModel 执行。
 */
class HybridGatewayClient(
    context: Context,
    apiBaseUrl: String,
    apiKey: String,
    apiModel: String,
    localModelId: String = LocalModelStore.DEFAULT_MODEL_ID,
    hfToken: String = "",
    private val modelStore: LocalModelStore = LocalModelStore(context),
    private val routeModeProvider: () -> GatewayRouteMode = { GatewayRouteMode.AUTO },
) : StreamingChatClient {

    private val appContext = context.applicationContext
    private val resolvedLocalModelId = when {
        localModelId.isBlank() ||
            localModelId == "default" ||
            localModelId == "deepseek-chat" ||
            !modelStore.isKnown(localModelId) -> LocalModelStore.DEFAULT_MODEL_ID
        else -> localModelId
    }
    private val apiConfigured = apiBaseUrl.isNotBlank() &&
        (apiBaseUrl.startsWith("http://", true) || apiBaseUrl.startsWith("https://", true))

    private val api: OpenAiCompatClient? = if (apiConfigured) {
        OpenAiCompatClient(
            baseUrl = apiBaseUrl,
            apiKey = apiKey,
            model = apiModel.ifBlank { "deepseek-chat" },
            localToolsEnabled = true,
            hermesSessionMode = false,
        )
    } else {
        null
    }

    private val local = LocalRuntimeClient(
        context = appContext,
        modelStore = modelStore,
        modelId = resolvedLocalModelId,
        hfToken = hfToken,
    )

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** 上一轮实际走的通道，供气泡 provider 展示。 */
    private val _lastRouteLabel = MutableStateFlow("网关")
    val lastRouteLabel: StateFlow<String> = _lastRouteLabel.asStateFlow()

    override suspend fun ensureConnected() {
        local.ensureConnected()
        api?.ensureConnected()
        _connected.value = true
    }

    override fun resetConversation() {
        api?.resetConversation()
        local.resetConversation()
    }

    override fun streamChat(
        prompt: String,
        history: List<ChatTurn>,
    ): Flow<String> = flow {
        val localReady = modelStore.isReady(resolvedLocalModelId)
        val mode = routeModeProvider()
        val route = GatewayRouter.decide(
            prompt = prompt,
            localReady = localReady,
            apiConfigured = api != null,
            mode = mode,
        )

        when (route) {
            GatewayRouter.Route.API -> {
                val client = api ?: error("未配置 API，且本地模型不可用")
                _lastRouteLabel.value = "网关·API"
                client.streamChat(prompt, history).collect { emit(it) }
            }
            GatewayRouter.Route.LOCAL -> {
                _lastRouteLabel.value = "网关·本地"
                val buffer = StringBuilder()
                local.streamChat(prompt, history).collect { piece ->
                    buffer.append(piece)
                }
                val localText = buffer.toString()
                // 仅自动模式：本地弱回复再 escalate；手选本地不擅自改走云端
                if (mode == GatewayRouteMode.AUTO &&
                    api != null &&
                    GatewayRouter.isWeakLocalReply(localText)
                ) {
                    _lastRouteLabel.value = "网关·API"
                    api.streamChat(prompt, history).collect { emit(it) }
                } else {
                    if (localText.isNotEmpty()) emit(localText)
                }
            }
        }
        _connected.value = true
    }

    override fun close() {
        api?.close()
        local.close()
        _connected.value = false
    }
}
