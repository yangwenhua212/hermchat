package com.eraherm.hermchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.AgentStore
import com.eraherm.hermchat.data.local.ConversationRepository
import com.eraherm.hermchat.data.local.MessageRepository
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.data.model.Conversation
import com.eraherm.hermchat.data.model.Message
import com.eraherm.hermchat.data.model.MessageRole
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.network.AIClientFactory
import com.eraherm.hermchat.data.network.AgentFailover
import com.eraherm.hermchat.data.network.AgentSessionHolder
import com.eraherm.hermchat.data.network.ApiChatMessage
import com.eraherm.hermchat.data.network.ChatTurn
import com.eraherm.hermchat.data.network.HybridGatewayClient
import com.eraherm.hermchat.data.network.paceForDisplay
import com.eraherm.hermchat.data.network.StreamingChatClient
import com.eraherm.hermchat.service.BridgeKeepAliveService
import com.eraherm.hermchat.service.VoiceEvent
import com.eraherm.hermchat.tools.LocalToolPlanner
import com.eraherm.hermchat.tools.LocalToolsPrompt
import com.eraherm.hermchat.tools.ToolCallParser
import com.eraherm.hermchat.tools.ToolRegistry
import com.eraherm.hermchat.util.UserFacingError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume


data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val isSending: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
    val agentName: String? = null,
    val pendingToolCall: ToolCall? = null,
    val toolExecuting: Boolean = false,
    val loopStep: LoopStep = LoopStep.Idle,
    val loopEscalate: LoopEscalateOffer? = null,
)

class ChatViewModel(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val agentStore: AgentStore,
    private val toolRegistry: ToolRegistry,
    private val appContext: android.content.Context,
) : ViewModel() {

    private val busy = MutableStateFlow(BusyFlags())
    // 拆分：用两个轻量 String Flow 代替 Message Flow，避免每次 token 都 copy() 建对象
    private val streamingContent = MutableStateFlow("")
    private val streamingProvider = MutableStateFlow<String?>(null)
    private val bridgeConnected = MutableStateFlow(false)
    private val activeAgent = MutableStateFlow<AgentProfile?>(null)
    private val pendingToolCall = MutableStateFlow<ToolCall?>(null)

    private val sessions: AgentSessionHolder =
        (appContext as HermChatApp).agentSessionHolder
    private val client: StreamingChatClient?
        get() = sessions.client
    private var sendJob: Job? = null
    private var agentJob: Job? = null
    private var currentAgentId: String? = null
    /** ④ Agent loop：等待确认工具后回灌 API 续跑。 */
    private var gatewayLoop: GatewayLoopState? = null
    /** 分级确认：挂起中的「允许/取消」回调；取消或换会话时 resume(false)。 */
    private var toolDecisionCont: Continuation<Boolean>? = null
    private val voiceSendHandler: (String) -> Unit = { text -> sendMessage(text) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val conversationsForAgent = activeAgent.flatMapLatest { agent ->
        val id = agent?.id
        if (id.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            conversationRepository.observeForAgent(id)
        }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        combine(
            messageRepository.observeMessages(),
            conversationsForAgent,
            conversationRepository.activeId,
            busy,
        ) { messages, conversations, activeId, flags ->
            Quad(messages, conversations, activeId, flags)
        },
        combine(
            streamingContent,
            streamingProvider,
            bridgeConnected,
            activeAgent,
            pendingToolCall,
        ) { content, provider, connected, agent, pending ->
            StreamingBundle(content, provider, connected, agent, pending)
        },
    ) { quad, stream ->
        val (messages, conversations, activeId, flags) = quad
        ChatUiState(
            messages = mergeStreaming(messages, stream.content, stream.provider),
            conversations = conversations,
            activeConversationId = activeId,
            isSending = flags.isSending,
            isStreaming = flags.isStreaming,
            error = flags.error,
            connected = stream.connected,
            agentName = stream.agent?.name,
            pendingToolCall = stream.pending,
            toolExecuting = flags.toolExecuting,
            loopStep = flags.loopStep,
            loopEscalate = flags.loopEscalate,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    init {
        (appContext as? HermChatApp)?.voiceCloudBridge?.bindForegroundSender(voiceSendHandler)
        agentJob = viewModelScope.launch {
            combine(agentStore.agents, agentStore.currentId) { agents, currentId ->
                agents.find { it.id == currentId } ?: agents.firstOrNull()
            }.collect { agent ->
                activeAgent.value = agent
                reconnect(agent)
            }
        }
    }

    fun sendMessage(text: String, enableLocalTools: Boolean = true) {
        val content = text.trim()
        if (content.isEmpty()) return
        if (busy.value.isSending || busy.value.isStreaming || busy.value.toolExecuting) return

        val agent = activeAgent.value
        val toolsOn = enableLocalTools && agent?.localToolsEnabled != false
        val localPlan = if (toolsOn) LocalToolPlanner.plan(content) else null

        sendJob?.cancel()
        rejectToolConfirmation()
        sendJob = viewModelScope.launch {
            conversationRepository.bootstrap(agent?.id)
            if (messageRepository.count() >= AUTO_NEW_CHAT_THRESHOLD) {
                startNewChatInternal()
            }

            busy.update {
                it.copy(
                    isSending = true,
                    error = null,
                    loopEscalate = null,
                    loopStep = LoopStep.Planning(),
                )
            }
            val assistantId = UUID.randomUUID().toString()
            try {
                messageRepository.save(
                    Message(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.USER,
                        content = content,
                        providerLabel = agent?.kind?.label,
                        createdAt = System.currentTimeMillis(),
                    ),
                )

                busy.update { it.copy(isSending = false, isStreaming = true) }
                streamingContent.value = ""
                streamingProvider.value = agent?.kind?.label

                val outcome = runCatching {
                    streamTurn(agent = agent, prompt = content, toolsOn = toolsOn)
                }.recoverCatching { primaryError ->
                    val failover = AgentFailover.pick(agent, agentStore.agents.value)
                        ?: throw primaryError
                    (appContext as? HermChatApp)?.voiceEventBus?.emit(
                        VoiceEvent.Status("改用 ${failover.name}…"),
                    )
                    busy.update { it.copy(error = null) }
                    setLoopStep(LoopStep.Planning("改用 ${failover.name}…"))
                    streamTurn(
                        agent = failover,
                        prompt = content,
                        toolsOn = toolsOn && failover.localToolsEnabled,
                        usePrimaryClient = false,
                        providerOverride = "备用·${failover.name}",
                    )
                }.getOrThrow()

                val effectiveTool = outcome.tool ?: localPlan
                messageRepository.save(
                    Message(
                        id = assistantId,
                        role = MessageRole.ASSISTANT,
                        content = outcome.text,
                        providerLabel = outcome.providerLabel,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                (appContext as? HermChatApp)?.voiceEventBus?.emit(
                    VoiceEvent.Status(outcome.text.take(48).ifBlank { "已回复" }),
                )

                if (effectiveTool != null) {
                    val seed = outcome.loopSeed
                    if (seed != null) {
                        if (!seed.lastAssistantRaw.contains("\"type\"") &&
                            !seed.lastAssistantRaw.contains(effectiveTool.name)
                        ) {
                            seed.lastAssistantRaw = listOf(
                                seed.lastAssistantRaw.trim(),
                                toolCallJson(effectiveTool),
                            ).filter { it.isNotBlank() }.joinToString("\n")
                        }
                        gatewayLoop = seed
                    }
                    streamingContent.value = ""
                    streamingProvider.value = null
                    busy.update { it.copy(isSending = false, isStreaming = false) }
                    runAuthorizedTool(effectiveTool)
                } else {
                    gatewayLoop = null
                    setLoopStep(LoopStep.Finished(outcome.text.take(40)))
                }
            } catch (e: Exception) {
                val friendly = UserFacingError.of(e, "发送失败")
                busy.update { it.copy(error = friendly, loopStep = LoopStep.Error(friendly)) }
                (appContext as? HermChatApp)?.voiceEventBus?.emit(
                    VoiceEvent.Error(friendly),
                )
                val partial = streamingContent.value
                if (partial.isNotBlank()) {
                    messageRepository.save(
                        Message(
                            id = assistantId,
                            role = MessageRole.ASSISTANT,
                            content = partial + "\n\n⚠️ $friendly",
                            providerLabel = agent?.kind?.label,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            } finally {
                streamingContent.value = ""
                streamingProvider.value = null
                busy.update {
                    it.copy(
                        isSending = false,
                        isStreaming = false,
                        loopStep = if (it.loopStep is LoopStep.Error) {
                            it.loopStep
                        } else {
                            LoopStep.Idle
                        },
                    )
                }
                bridgeConnected.value = client?.connected?.value == true
            }
        }
    }

    fun confirmPendingTool() {
        val cont = toolDecisionCont
        if (cont != null) {
            toolDecisionCont = null
            cont.resume(true)
            return
        }
    }

    fun denyPendingTool() {
        val cont = toolDecisionCont
        if (cont != null) {
            toolDecisionCont = null
            cont.resume(false)
            return
        }
        val call = pendingToolCall.value ?: return
        pendingToolCall.value = null
        gatewayLoop = null
        setLoopStep(LoopStep.Idle)
        viewModelScope.launch {
            messageRepository.save(
                Message(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.SYSTEM,
                    content = "已取消",
                    providerLabel = "tool",
                    createdAt = System.currentTimeMillis(),
                ),
            )
            runCatching {
                client?.sendToolResult(call.id, false, "user_denied")
            }
        }
    }

    private fun rejectToolConfirmation() {
        val cont = toolDecisionCont
        toolDecisionCont = null
        pendingToolCall.value = null
        if (cont != null) {
            runCatching { cont.resume(false) }
        }
    }

    /**
     * 按 [ToolRisk] 决定是否弹确认卡；写操作挂起协程等待 UI，取消则干净结束 loop。
     */
    private suspend fun runAuthorizedTool(call: ToolCall) {
        val risk = toolRegistry.riskFor(call.name)
        val allowed = if (risk.requiresUserConfirm) {
            awaitToolConfirmation(call)
        } else {
            true
        }
        if (!allowed) {
            gatewayLoop = null
            pendingToolCall.value = null
            setLoopStep(LoopStep.Idle)
            messageRepository.save(
                Message(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.SYSTEM,
                    content = "已取消",
                    providerLabel = "tool",
                    createdAt = System.currentTimeMillis(),
                ),
            )
            runCatching {
                client?.sendToolResult(call.id, false, "user_denied")
            }
            return
        }
        pendingToolCall.value = null
        val execDesc = call.title.ifBlank {
            LoopStep.friendlyToolName(call.name)
        }
        busy.update {
            it.copy(
                toolExecuting = true,
                error = null,
                loopStep = LoopStep.Executing(call.name, execDesc),
            )
        }
        val result = toolRegistry.execute(call.copy(needConfirm = true))
        busy.update { it.copy(toolExecuting = false) }
        setLoopStep(LoopStep.Observing(result.message.take(48)))

        messageRepository.save(
            Message(
                id = UUID.randomUUID().toString(),
                role = MessageRole.SYSTEM,
                content = if (result.success) {
                    "✅ ${result.message}"
                } else {
                    "⚠️ 操作失败：${result.message}。请手动处理。"
                },
                providerLabel = "tool",
                createdAt = System.currentTimeMillis(),
            ),
        )

        runCatching {
            client?.sendToolResult(result.toolCallId, result.success, result.message)
        }

        val loop = gatewayLoop
        val gateway = client as? HybridGatewayClient
        if (loop != null && gateway != null && gateway.hasApi) {
            delay(320)
            continueGatewayLoop(
                gateway = gateway,
                loop = loop,
                toolName = call.name,
                success = result.success,
                detail = result.message,
            )
        } else {
            gatewayLoop = null
            setLoopStep(LoopStep.Idle)
        }
    }

    private suspend fun awaitToolConfirmation(call: ToolCall): Boolean {
        // 不 resume 旧回调为 false 再挂起：仅清掉残留 UI，旧 cont 若存在属异常态
        if (toolDecisionCont != null) {
            rejectToolConfirmation()
        }
        pendingToolCall.value = call
        setLoopStep(LoopStep.Idle)
        return suspendCancellableCoroutine { cont ->
            toolDecisionCont = cont
            cont.invokeOnCancellation {
                if (toolDecisionCont === cont) {
                    toolDecisionCont = null
                }
                pendingToolCall.value = null
            }
        }
    }

    fun clearError() {
        busy.update {
            it.copy(
                error = null,
                loopEscalate = null,
                loopStep = if (it.loopStep is LoopStep.Error) LoopStep.Idle else it.loopStep,
            )
        }
    }

    /** 超步数一键切到已保存的 ③；无目标时返回 false，由 UI 去添加。 */
    fun switchToLoopEscalateTarget(): Boolean {
        val offer = busy.value.loopEscalate ?: return false
        val id = offer.targetAgentId?.takeIf { it.isNotBlank() } ?: return false
        busy.update {
            it.copy(error = null, loopEscalate = null, loopStep = LoopStep.Idle)
        }
        gatewayLoop = null
        agentStore.setCurrentId(id)
        return true
    }

    fun dismissLoopEscalate() {
        busy.update {
            it.copy(error = null, loopEscalate = null, loopStep = LoopStep.Idle)
        }
    }

    fun onForeground() {
        viewModelScope.launch {
            val agent = activeAgent.value ?: return@launch
            val existing = client
            when {
                existing == null -> softRebind(agent)
                isHttpStyle(agent) -> {
                    bridgeConnected.value = true
                }
                else -> {
                    runCatching {
                        existing.ensureConnected()
                        bridgeConnected.value = existing.connected.value
                    }.onFailure {
                        softRebind(agent)
                    }
                    if (client?.connected?.value != true) {
                        softRebind(agent)
                    }
                }
            }
        }
    }

    fun startNewChat() {
        if (busy.value.isSending || busy.value.isStreaming) return
        viewModelScope.launch {
            startNewChatInternal()
        }
    }

    fun openConversation(id: String) {
        if (busy.value.isSending || busy.value.isStreaming) return
        if (id == conversationRepository.activeId.value) return
        viewModelScope.launch {
            conversationRepository.setActive(id)
            streamingContent.value = ""
            streamingProvider.value = null
            rejectToolConfirmation()
            gatewayLoop = null
            setLoopStep(LoopStep.Idle)
            // 旧会话本地可回看；服务端 Session 不自动恢复，避免串到上一会话
            sessions.client?.resetConversation()
            bridgeConnected.value = sessions.client != null &&
                (sessions.client?.connected?.value == true || activeAgent.value?.let { isHttpStyle(it) } == true)
            ensureWelcomeMessage()
        }
    }

    fun deleteConversation(id: String) {
        if (busy.value.isSending || busy.value.isStreaming) return
        viewModelScope.launch {
            val wasActive = conversationRepository.activeId.value == id
            messageRepository.deleteConversationMessages(id)
            conversationRepository.delete(id, preferAgentId = activeAgent.value?.id)
            streamingContent.value = ""
            streamingProvider.value = null
            rejectToolConfirmation()
            gatewayLoop = null
            if (conversationRepository.activeId.value == null) {
                conversationRepository.createNew(activeAgent.value?.id)
                sessions.client?.resetConversation()
            } else if (wasActive) {
                sessions.client?.resetConversation()
            }
            ensureWelcomeMessage()
        }
    }

    private suspend fun startNewChatInternal() {
        rejectToolConfirmation()
        conversationRepository.createNew(activeAgent.value?.id)
        streamingContent.value = ""
        streamingProvider.value = null
        pendingToolCall.value = null
        gatewayLoop = null
        setLoopStep(LoopStep.Idle)
        // 只换会话，不断开底层连接（后台保活仍有效）；旧消息留在历史列表
        sessions.client?.resetConversation()
        bridgeConnected.value = sessions.client != null &&
            (sessions.client?.connected?.value == true || activeAgent.value?.let { isHttpStyle(it) } == true)
        ensureWelcomeMessage()
    }

    fun permissionsForPendingTool(): Array<String> {
        val name = pendingToolCall.value?.name ?: return emptyArray()
        return toolRegistry.requiredPermissions(name)
    }

    override fun onCleared() {
        super.onCleared()
        (appContext as? HermChatApp)?.voiceCloudBridge?.unbindForegroundSender(voiceSendHandler)
        sendJob?.cancel()
        agentJob?.cancel()
        // 不关 sessions.client：回桌面/重建 Activity 后复用；保活服务继续撑进程
        BridgeKeepAliveService.sync(appContext)
    }

    private suspend fun reconnect(agent: AgentProfile?) {
        if (agent == null) {
            currentAgentId = null
            sessions.release(close = true)
            BridgeKeepAliveService.stop(appContext)
            bridgeConnected.value = false
            return
        }
        if (agent.id != currentAgentId) {
            val previous = currentAgentId
            currentAgentId = agent.id
            gatewayLoop = null
            rejectToolConfirmation()
            setLoopStep(LoopStep.Idle)
            if (previous == null) {
                conversationRepository.claimOrphanConversations(agent.id)
                conversationRepository.bootstrap(agent.id)
                conversationRepository.stampActiveAgent(agent.id)
            } else {
                conversationRepository.activateForAgent(agent.id)
                if (!sessions.matches(agent)) {
                    sessions.client?.resetConversation()
                }
            }
        }
        // 同一 Agent 且已有连接：复用，避免退出再进反复建连
        if (sessions.matches(agent)) {
            runCatching {
                sessions.client?.ensureConnected()
                bridgeConnected.value =
                    sessions.client?.connected?.value == true || isHttpStyle(agent)
                BridgeKeepAliveService.sync(appContext)
                ensureWelcomeMessage()
            }.onFailure {
                softRebind(agent)
            }
            return
        }
        sessions.release(close = true)
        bridgeConnected.value = false
        runCatching {
            val created = AIClientFactory.create(agent, appContext)
            created.ensureConnected()
            sessions.attach(agent, created)
            bridgeConnected.value = created.connected.value || isHttpStyle(agent)
            BridgeKeepAliveService.sync(appContext)
            ensureWelcomeMessage()
        }.onFailure { err ->
            bridgeConnected.value = false
            BridgeKeepAliveService.stop(appContext)
            busy.update {
                it.copy(
                    error = "未能连接 ${agent.name}：${UserFacingError.of(err, "请检查地址与网络")}",
                )
            }
        }
    }

    private suspend fun softRebind(agent: AgentProfile) {
        sessions.release(close = true)
        bridgeConnected.value = false
        runCatching {
            val created = AIClientFactory.create(agent, appContext)
            created.ensureConnected()
            sessions.attach(agent, created)
            bridgeConnected.value = created.connected.value || isHttpStyle(agent)
            BridgeKeepAliveService.sync(appContext)
        }.onFailure { err ->
            bridgeConnected.value = false
            BridgeKeepAliveService.stop(appContext)
            busy.update {
                it.copy(
                    error = "连接已断开：${UserFacingError.of(err, "请再试一次")}",
                )
            }
        }
    }

    private data class GatewayLoopState(
        val messages: MutableList<ApiChatMessage>,
        var lastAssistantRaw: String,
        var step: Int,
    )

    private data class TurnOutcome(
        val text: String,
        val tool: ToolCall?,
        val providerLabel: String?,
        val loopSeed: GatewayLoopState? = null,
    )

    private suspend fun streamTurn(
        agent: AgentProfile?,
        prompt: String,
        toolsOn: Boolean,
        usePrimaryClient: Boolean = true,
        providerOverride: String? = null,
    ): TurnOutcome {
        val chatClient = if (usePrimaryClient) {
            client ?: run {
                val profile = agent ?: error("请先配置 Agent")
                val created = AIClientFactory.create(profile, appContext)
                sessions.attach(profile, created)
                BridgeKeepAliveService.sync(appContext)
                created
            }
        } else {
            agent?.let { AIClientFactory.create(it, appContext) }
                ?: error("没有备用 Agent")
        }
        try {
            chatClient.ensureConnected()
            if (usePrimaryClient) {
                bridgeConnected.value = chatClient.connected.value
            }
            val provider = providerOverride ?: agent?.kind?.label
            streamingProvider.value = provider

            val history = buildHttpHistory(agent)
            val buffer = StringBuilder()
            chatClient.streamChat(prompt, history).paceForDisplay().collect { token ->
                buffer.append(token)
                streamingContent.value = buffer.toString()
                val route = (chatClient as? HybridGatewayClient)?.lastRouteLabel?.value
                if (route != null) streamingProvider.value = providerOverride ?: route ?: provider
            }

            val raw = buffer.toString()
            val (displayText, agentTool) = if (toolsOn) {
                ToolCallParser.extract(raw)
            } else {
                raw to null
            }
            val finalText = displayText.ifBlank {
                if (agentTool != null) {
                    "好的。"
                } else {
                    "（空回复）"
                }
            }
            val routeLabel = (chatClient as? HybridGatewayClient)?.lastRouteLabel?.value
            val gateway = chatClient as? HybridGatewayClient
            val loopSeed = if (
                agent?.kind == AgentKind.GATEWAY &&
                gateway != null &&
                gateway.hasApi
            ) {
                GatewayLoopState(
                    messages = gateway.buildApiTurnMessages(prompt, history).toMutableList(),
                    lastAssistantRaw = raw.ifBlank { finalText },
                    step = 1,
                )
            } else {
                null
            }
            return TurnOutcome(
                text = finalText,
                tool = agentTool,
                providerLabel = providerOverride ?: routeLabel ?: provider,
                loopSeed = loopSeed,
            )
        } finally {
            if (!usePrimaryClient) {
                chatClient.close()
            }
        }
    }

    private suspend fun continueGatewayLoop(
        gateway: HybridGatewayClient,
        loop: GatewayLoopState,
        toolName: String,
        success: Boolean,
        detail: String,
    ) {
        if (loop.step >= GATEWAY_LOOP_MAX_STEPS) {
            gatewayLoop = null
            offerLoopEscalate()
            return
        }
        val assistantId = UUID.randomUUID().toString()
        busy.update {
            it.copy(
                isStreaming = true,
                error = null,
                loopStep = LoopStep.Planning("第 ${loop.step + 1} 步…"),
            )
        }
        streamingContent.value = ""
        streamingProvider.value = "网关·API"
        try {
            loop.messages.add(ApiChatMessage("assistant", loop.lastAssistantRaw))
            loop.messages.add(
                ApiChatMessage(
                    "user",
                    LocalToolsPrompt.toolResultUserMessage(toolName, success, detail),
                ),
            )
            loop.step += 1

            val buffer = StringBuilder()
            gateway.streamApiMessages(loop.messages).paceForDisplay().collect { token ->
                buffer.append(token)
                streamingContent.value = buffer.toString()
                streamingProvider.value = gateway.lastRouteLabel.value
            }
            val raw = buffer.toString()
            val (displayText, agentTool) = ToolCallParser.extract(raw)
            val finalText = displayText.ifBlank {
                if (agentTool != null) "好的。" else "（空回复）"
            }
            messageRepository.save(
                Message(
                    id = assistantId,
                    role = MessageRole.ASSISTANT,
                    content = finalText,
                    providerLabel = gateway.lastRouteLabel.value,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (agentTool != null) {
                loop.lastAssistantRaw = raw.ifBlank { finalText }
                gatewayLoop = loop
                streamingContent.value = ""
                streamingProvider.value = null
                busy.update { it.copy(isStreaming = false) }
                runAuthorizedTool(agentTool)
            } else {
                gatewayLoop = null
                setLoopStep(LoopStep.Finished(finalText.take(40)))
            }
        } catch (e: Exception) {
            gatewayLoop = null
            val friendly = UserFacingError.of(e, "继续回复失败")
            busy.update { it.copy(error = friendly, loopStep = LoopStep.Error(friendly)) }
            val partial = streamingContent.value
            if (partial.isNotBlank()) {
                messageRepository.save(
                    Message(
                        id = assistantId,
                        role = MessageRole.ASSISTANT,
                        content = partial + "\n\n⚠️ $friendly",
                        providerLabel = "网关·API",
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        } finally {
            streamingContent.value = ""
            streamingProvider.value = null
            busy.update {
                it.copy(
                    isStreaming = false,
                    loopStep = if (it.loopStep is LoopStep.Error) {
                        it.loopStep
                    } else {
                        LoopStep.Idle
                    },
                )
            }
        }
    }

    private fun toolCallJson(call: ToolCall): String {
        val args = org.json.JSONObject()
        call.arguments.forEach { (k, v) ->
            val asLong = v.toLongOrNull()
            if (asLong != null && (k.endsWith("Ms") || k == "triggerMs" || k == "beginMs" || k == "endMs")) {
                args.put(k, asLong)
            } else {
                args.put(k, v)
            }
        }
        return org.json.JSONObject()
            .put("type", "tool_call")
            .put("id", call.id)
            .put("name", call.name)
            .put("arguments", args)
            .toString()
    }

    private fun isHttpStyle(agent: AgentProfile): Boolean {
        val ep = agent.endpoint.trim()
        return agent.kind == AgentKind.HERMES ||
            agent.kind == AgentKind.HTTP_COMPAT ||
            agent.kind == AgentKind.GATEWAY ||
            ep.startsWith("http://", ignoreCase = true) ||
            ep.startsWith("https://", ignoreCase = true)
    }

    private suspend fun buildHttpHistory(agent: AgentProfile?): List<ChatTurn> {
        if (agent == null) return emptyList()
        if (agent.kind != AgentKind.HTTP_COMPAT && agent.kind != AgentKind.GATEWAY) {
            return emptyList()
        }
        return messageRepository.recentChronological(16)
            .filter { !it.id.startsWith(WELCOME_PREFIX) }
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .map { msg ->
                ChatTurn(
                    role = if (msg.role == MessageRole.ASSISTANT) "assistant" else "user",
                    content = msg.content,
                )
            }
    }

    /** 只有流式中有内容时才拼装临时 Message，避免每次 token 都 new 对象。 */
    private fun mergeStreaming(
        messages: List<Message>,
        content: String,
        provider: String?,
    ): List<Message> {
        if (content.isEmpty()) return messages
        val streamingMsg = Message(
            id = "streaming-tmp",
            role = MessageRole.ASSISTANT,
            content = content,
            providerLabel = provider,
            createdAt = System.currentTimeMillis(),
            conversationId = conversationRepository.activeId.value.orEmpty(),
        )
        return messages + streamingMsg
    }

    private suspend fun ensureWelcomeMessage() {
        conversationRepository.bootstrap(activeAgent.value?.id)
        if (!messageRepository.isEmpty()) return
        val conversationId = conversationRepository.activeId.value ?: return
        messageRepository.save(
            Message(
                id = welcomeId(conversationId),
                role = MessageRole.ASSISTANT,
                content = "HxSync 已就绪。",
                providerLabel = "local",
                createdAt = System.currentTimeMillis(),
                conversationId = conversationId,
            ),
        )
    }

    private fun offerLoopEscalate() {
        val remote = LoopEscalatePicker.pick(activeAgent.value, agentStore.agents.value)
        val offer = if (remote != null) {
            LoopEscalateOffer(targetAgentId = remote.id, targetName = remote.name)
        } else {
            LoopEscalateOffer(targetAgentId = null, targetName = null)
        }
        val msg = if (offer.hasTarget) {
            "步骤较多，可切换继续"
        } else {
            "步骤较多，请添加远端 Agent"
        }
        busy.update {
            it.copy(
                error = msg,
                loopEscalate = offer,
                loopStep = LoopStep.Error(msg),
            )
        }
        viewModelScope.launch {
            messageRepository.save(
                Message(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.SYSTEM,
                    content = msg,
                    providerLabel = "loop",
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private data class BusyFlags(
        val isSending: Boolean = false,
        val isStreaming: Boolean = false,
        val toolExecuting: Boolean = false,
        val error: String? = null,
        val loopStep: LoopStep = LoopStep.Idle,
        val loopEscalate: LoopEscalateOffer? = null,
    )

    private fun setLoopStep(step: LoopStep) {
        busy.update { it.copy(loopStep = step) }
    }

    /** 4 元组，避免嵌套 combine 时的 Pair/Triple 装箱。 */
    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

    private data class StreamingBundle(
        val content: String,
        val provider: String?,
        val connected: Boolean,
        val agent: AgentProfile?,
        val pending: ToolCall?,
    )

    companion object {
        private const val WELCOME_PREFIX = "welcome-"
        private const val AUTO_NEW_CHAT_THRESHOLD = 20
        private const val GATEWAY_LOOP_MAX_STEPS = 8

        private fun welcomeId(conversationId: String): String = "$WELCOME_PREFIX$conversationId"

        fun factory(
            repository: MessageRepository,
            conversationRepository: ConversationRepository,
            agentStore: AgentStore,
            toolRegistry: ToolRegistry,
            appContext: android.content.Context,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        return ChatViewModel(
                            repository,
                            conversationRepository,
                            agentStore,
                            toolRegistry,
                            appContext.applicationContext,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
    }
}
