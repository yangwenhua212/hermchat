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
 * ④ 端侧 Agent 网关：本地/API 路由；API 路径支持多轮 tool 回灌（见 [streamApiMessages]）。
 */
class HybridGatewayClient(
    context: Context,
    apiBaseUrl: String,
    apiKey: String = "",
    apiModel: String,
    localModelId: String = LocalModelStore.DEFAULT_MODEL_ID,
    hfToken: String = "",
    private val modelStore: LocalModelStore = LocalModelStore(context),
    private val routeModeProvider: () -> GatewayRouteMode = { GatewayRouteMode.API },
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

    private val _lastRouteLabel = MutableStateFlow("网关")
    val lastRouteLabel: StateFlow<String> = _lastRouteLabel.asStateFlow()

    val hasApi: Boolean get() = api != null

    fun isLocalReady(): Boolean = modelStore.isReady(resolvedLocalModelId)

    fun markLocalRoute() {
        _lastRouteLabel.value = "网关·本地"
    }

    /**
     * 实验「本地优先解析」：本地试跑 tool 协议文本；未就绪或异常返回 null。
     */
    suspend fun tryLocalToolPlan(prompt: String): String? = local.tryGenerateToolPlan(prompt)

    /** ④ Agent loop 续跑：强制走 API（工具结果回灌）。 */
    fun streamApiMessages(messages: List<ApiChatMessage>): Flow<String> {
        val client = api ?: error("未配置 API，无法继续 Agent 步骤")
        _lastRouteLabel.value = "网关·API"
        return client.streamMessages(messages)
    }

    /** 首轮强制云端（本地优先解析失败后的保底 / 识图）。 */
    fun streamApiChat(
        prompt: String,
        history: List<ChatTurn>,
        imageDataUrl: String? = null,
    ): Flow<String> {
        val client = api ?: error("未配置 API")
        _lastRouteLabel.value = "网关·API"
        return client.streamChat(prompt, history, imageDataUrl)
    }

    fun buildApiTurnMessages(
        prompt: String,
        history: List<ChatTurn>,
        imageDataUrl: String? = null,
    ): List<ApiChatMessage> {
        val client = api ?: error("未配置 API")
        return client.buildTurnMessages(prompt, history, imageDataUrl)
    }

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
        imageDataUrl: String?,
    ): Flow<String> = flow {
        if (!imageDataUrl.isNullOrBlank()) {
            val client = api ?: error("未配置 API，无法识图")
            _lastRouteLabel.value = "网关·API"
            client.streamChat(prompt, history, imageDataUrl).collect { emit(it) }
            _connected.value = true
            return@flow
        }

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
                local.streamChat(prompt, history).collect { emit(it) }
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
