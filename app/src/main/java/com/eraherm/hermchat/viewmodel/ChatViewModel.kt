package com.eraherm.hermchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eraherm.hermchat.data.local.AgentStore
import com.eraherm.hermchat.data.local.MessageRepository
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.data.model.Message
import com.eraherm.hermchat.data.model.MessageRole
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.network.AIClientFactory
import com.eraherm.hermchat.data.network.paceForDisplay
import com.eraherm.hermchat.data.network.StreamingChatClient
import com.eraherm.hermchat.tools.LocalToolPlanner
import com.eraherm.hermchat.tools.ToolCallParser
import com.eraherm.hermchat.tools.ToolRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isSending: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
    val agentName: String? = null,
    val pendingToolCall: ToolCall? = null,
    val toolExecuting: Boolean = false,
)

class ChatViewModel(
    private val messageRepository: MessageRepository,
    private val agentStore: AgentStore,
    private val toolRegistry: ToolRegistry,
    private val appContext: android.content.Context,
) : ViewModel() {

    private val busy = MutableStateFlow(BusyFlags())
    private val streamingMessage = MutableStateFlow<Message?>(null)
    private val bridgeConnected = MutableStateFlow(false)
    private val activeAgent = MutableStateFlow<AgentProfile?>(null)
    private val pendingToolCall = MutableStateFlow<ToolCall?>(null)

    private var client: StreamingChatClient? = null
    private var sendJob: Job? = null
    private var agentJob: Job? = null
    private var currentAgentId: String? = null

    val uiState: StateFlow<ChatUiState> = combine(
        combine(
            messageRepository.observeMessages(),
            busy,
            streamingMessage,
        ) { messages, flags, streaming ->
            Triple(messages, flags, streaming)
        },
        combine(bridgeConnected, activeAgent, pendingToolCall) { connected, agent, pending ->
            Triple(connected, agent, pending)
        },
    ) { msgTriple, agentTriple ->
        val (messages, flags, streaming) = msgTriple
        val (connected, agent, pending) = agentTriple
        ChatUiState(
            messages = mergeStreaming(messages, streaming),
            isSending = flags.isSending,
            isStreaming = flags.isStreaming,
            error = flags.error,
            connected = connected,
            agentName = agent?.name,
            pendingToolCall = pending,
            toolExecuting = flags.toolExecuting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    init {
        viewModelScope.launch { ensureWelcomeMessage() }
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
        val localPlan = if (enableLocalTools) LocalToolPlanner.plan(content) else null

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            // ── 长对话自动开新会话 ──
            // Hermes bridge 的 session 常驻服务端，上下文只增不减会越聊越慢。
            // 超过阈值后强制换新 session：清空本地历史 + 断开重建连接，
            // 下次发送会 session.create 一个新会话，上下文立刻归零。
            if (messageRepository.count() >= AUTO_NEW_CHAT_THRESHOLD) {
                startNewChatInternal()
            }

            busy.update { it.copy(isSending = true, error = null) }
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

                // Show confirm card early for clear schedule intents.
                if (localPlan != null) {
                    pendingToolCall.value = localPlan
                }

                val chatClient = client ?: run {
                    val created = agent?.let { AIClientFactory.create(it, appContext) }
                        ?: error("请先配置 Agent")
                    client = created
                    created
                }
                chatClient.ensureConnected()
                bridgeConnected.value = chatClient.connected.value

                busy.update { it.copy(isSending = false, isStreaming = true) }
                streamingMessage.value = Message(
                    id = assistantId,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    providerLabel = agent?.kind?.label,
                    createdAt = System.currentTimeMillis(),
                )

                val buffer = StringBuilder()
                chatClient.streamChat(content).paceForDisplay(charDelayMs = 28L).collect { token ->
                    buffer.append(token)
                    streamingMessage.value = streamingMessage.value?.copy(content = buffer.toString())
                }

                val raw = buffer.toString()
                val (displayText, agentTool) = ToolCallParser.extract(raw)
                if (agentTool != null) {
                    pendingToolCall.value = agentTool.copy(needConfirm = true)
                }

                val finalText = displayText.ifBlank {
                    if (pendingToolCall.value != null) {
                        "需要你确认后，我才能操作手机。"
                    } else {
                        "（空回复）"
                    }
                }
                messageRepository.save(
                    Message(
                        id = assistantId,
                        role = MessageRole.ASSISTANT,
                        content = finalText,
                        providerLabel = agent?.kind?.label,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                busy.update { it.copy(error = e.message ?: "发送失败") }
                if (streamingMessage.value != null) {
                    val partial = streamingMessage.value?.content.orEmpty()
                    if (partial.isNotBlank()) {
                        messageRepository.save(
                            Message(
                                id = assistantId,
                                role = MessageRole.ASSISTANT,
                                content = partial + "\n\n⚠️ ${e.message ?: "中断"}",
                                providerLabel = agent?.kind?.label,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            } finally {
                streamingMessage.value = null
                busy.update { it.copy(isSending = false, isStreaming = false) }
                bridgeConnected.value = client?.connected?.value == true
            }
        }
    }

    fun confirmPendingTool() {
        val call = pendingToolCall.value ?: return
        viewModelScope.launch {
            busy.update { it.copy(toolExecuting = true, error = null) }
            val result = toolRegistry.execute(call)
            pendingToolCall.value = null
            busy.update { it.copy(toolExecuting = false) }

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
        }
    }

    fun denyPendingTool() {
        val call = pendingToolCall.value ?: return
        pendingToolCall.value = null
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

    fun clearError() {
        busy.update { it.copy(error = null) }
    }

    /**
     * 新建对话：清空本地消息 + 强制换服务端会话。
     * 调用后下一次发送消息会走全新的 session（上下文归零，秒回）。
     * UI 入口暂未绑定，先作为公共接口暴露。
     */
    fun startNewChat() {
        if (busy.value.isSending || busy.value.isStreaming) return
        viewModelScope.launch {
            startNewChatInternal()
        }
    }

    private suspend fun startNewChatInternal() {
        messageRepository.clear()
        // 断开并丢弃当前 client：HermesBridgeClient.close() 会清掉 sessionId，
        // 置 null 后下次 sendMessage 重新 create → 新的 WebSocket + session.create。
        client?.close()
        client = null
        bridgeConnected.value = false
    }

    fun permissionsForPendingTool(): Array<String> {
        val name = pendingToolCall.value?.name ?: return emptyArray()
        return toolRegistry.requiredPermissions(name)
    }

    override fun onCleared() {
        super.onCleared()
        sendJob?.cancel()
        agentJob?.cancel()
        client?.close()
        client = null
    }

    private suspend fun reconnect(agent: AgentProfile?) {
        // 切换 agent 时：清空本地消息，避免聊天页还挂着上一个 agent 的历史，
        // 且新 agent 的服务端 session 是全新的（无旧上下文），旧消息留着会造成错位。
        if (agent?.id != currentAgentId) {
            currentAgentId = agent?.id
            messageRepository.clear()
        }
        client?.close()
        client = null
        bridgeConnected.value = false
        if (agent == null) return
        runCatching {
            val created = AIClientFactory.create(agent, appContext)
            created.ensureConnected()
            client = created
            bridgeConnected.value = created.connected.value
        }.onFailure { err ->
            bridgeConnected.value = false
            busy.update {
                it.copy(error = "未能连接 ${agent.name}：${err.message ?: "未知错误"}")
            }
        }
    }

    private fun mergeStreaming(
        messages: List<Message>,
        streaming: Message?,
    ): List<Message> {
        if (streaming == null) return messages
        return messages + streaming
    }

    private suspend fun ensureWelcomeMessage() {
        if (!messageRepository.isEmpty()) return
        messageRepository.save(
            Message(
                id = WELCOME_ID,
                role = MessageRole.ASSISTANT,
                content = "HxSync 已就绪。",
                providerLabel = "local",
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private data class BusyFlags(
        val isSending: Boolean = false,
        val isStreaming: Boolean = false,
        val toolExecuting: Boolean = false,
        val error: String? = null,
    )

    companion object {
        private const val WELCOME_ID = "welcome-local"

        /**
         * 超过该条数的本地消息后，下一次发送会自动开新会话
         * （清历史 + 换服务端 session，避免上下文无限膨胀越聊越慢）。
         */
        private const val AUTO_NEW_CHAT_THRESHOLD = 20

        fun factory(
            repository: MessageRepository,
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
