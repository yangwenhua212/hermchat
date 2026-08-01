package com.eraherm.hermchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eraherm.hermchat.data.local.AgentStore
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.data.network.ConnectionTester
import com.eraherm.hermchat.data.network.EndpointProbe
import com.eraherm.hermchat.data.network.SetupAssistDraft
import com.eraherm.hermchat.data.network.SetupAssistParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AssistChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromUser: Boolean,
    val text: String,
)

data class SetupAssistUiState(
    val messages: List<AssistChatMessage> = emptyList(),
    val draft: SetupAssistDraft = SetupAssistDraft(),
    val pendingConfirm: Boolean = false,
    val busy: Boolean = false,
    val completedProfile: AgentProfile? = null,
)

class SetupAssistViewModel(
    private val agentStore: AgentStore,
    private val endpointProbe: EndpointProbe,
    private val connectionTester: ConnectionTester = ConnectionTester(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SetupAssistUiState(
            messages = listOf(
                AssistChatMessage(
                    fromUser = false,
                    text = "可以说「连电脑上的助手」自动探测 WebSocket，或「连一下 47.x.x.x，密码是 sk-…」配 Hermes。",
                ),
            ),
        ),
    )
    val uiState: StateFlow<SetupAssistUiState> = _uiState.asStateFlow()

    fun prepareFreshSession() {
        _uiState.value = SetupAssistUiState(
            messages = listOf(
                AssistChatMessage(
                    fromUser = false,
                    text = "可以说「连电脑上的助手」自动探测 WebSocket，或「连一下 47.x.x.x，密码是 sk-…」配 Hermes。DeepSeek 等可说「http 兼容」。",
                ),
            ),
        )
    }

    fun consumeCompleted() {
        _uiState.update { it.copy(completedProfile = null) }
    }

    fun sendUserText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _uiState.value.busy) return

        viewModelScope.launch {
            append(fromUser = true, text = text)

            if (_uiState.value.pendingConfirm) {
                when {
                    isConfirm(text) -> {
                        _uiState.update { it.copy(pendingConfirm = false) }
                        tryConnect(allowEmptyKey = true)
                        return@launch
                    }
                    isRefill(text) -> {
                        refill()
                        return@launch
                    }
                }
            }

            if (text == "跳过" || text.equals("skip", true)) {
                offerConfirm(allowEmptyKey = true)
                return@launch
            }

            val parsed = SetupAssistParser.parse(text)
            val merged = _uiState.value.draft.merge(parsed)
            _uiState.update { it.copy(draft = merged, pendingConfirm = false) }

            if (merged.wantsLanProbe || !merged.probeHost.isNullOrBlank()) {
                runProbeAndConfirm(merged)
                return@launch
            }

            val missing = merged.missingHints()
            if (missing.isNotEmpty()) {
                append(
                    fromUser = false,
                    text = "还差${missing.joinToString("、")}。也可说「连电脑上的助手」让我自动探测。",
                )
                return@launch
            }

            val kind = merged.resolvedKind()
            val needKeyHint = kind == AgentKind.HERMES ||
                kind == AgentKind.HTTP_COMPAT ||
                kind == AgentKind.GATEWAY
            if (needKeyHint && merged.apiKey.isNullOrBlank()) {
                append(
                    fromUser = false,
                    text = "已记下 ${merged.endpoint}。把 API Key / 密码发我；没有就回复「跳过」。",
                )
                return@launch
            }

            offerConfirm(allowEmptyKey = false)
        }
    }

    fun confirmConnect() {
        if (_uiState.value.busy || !_uiState.value.pendingConfirm) return
        viewModelScope.launch {
            append(fromUser = true, text = "确认")
            _uiState.update { it.copy(pendingConfirm = false) }
            tryConnect(allowEmptyKey = true)
        }
    }

    fun refill() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    draft = SetupAssistDraft(),
                    pendingConfirm = false,
                )
            }
            append(fromUser = false, text = "好，请重新发一段。")
        }
    }

    private suspend fun runProbeAndConfirm(draft: SetupAssistDraft) {
        val kind = draft.resolvedKind().let {
            if (it == AgentKind.HERMES && draft.wantsLanProbe) AgentKind.WEBSOCKET else it
        }.let { if (it == AgentKind.CUSTOM) AgentKind.WEBSOCKET else it }

        _uiState.update { it.copy(busy = true) }
        val host = draft.probeHost
        append(
            fromUser = false,
            text = if (host.isNullOrBlank()) {
                "正在局域网探测 WebSocket…"
            } else {
                "正在探测 $host 上的 WebSocket…"
            },
        )

        val hits = runCatching {
            if (!host.isNullOrBlank()) {
                endpointProbe.discoverOnHost(host, AgentKind.WEBSOCKET)
                    .ifEmpty { endpointProbe.discoverOnHost(host, kind) }
            } else {
                endpointProbe.discover(AgentKind.WEBSOCKET)
            }
        }.getOrElse { emptyList() }

        _uiState.update { it.copy(busy = false) }

        if (hits.isEmpty()) {
            append(
                fromUser = false,
                text = "附近没找到可达端点。请发完整地址，例如 ws://192.168.1.8:8765/ws",
            )
            _uiState.update {
                it.copy(
                    draft = draft.copy(wantsLanProbe = false, probeHost = null),
                )
            }
            return
        }

        val best = hits.first()
        val extras = if (hits.size > 1) {
            "（另有 ${hits.size - 1} 个也可达）"
        } else {
            ""
        }
        val next = draft.copy(
            kind = AgentKind.WEBSOCKET,
            endpoint = best.endpoint,
            wantsLanProbe = false,
            probeHost = null,
            name = draft.name ?: AgentKind.WEBSOCKET.defaultName,
        )
        _uiState.update { it.copy(draft = next) }
        append(
            fromUser = false,
            text = "探测到 ${best.endpoint}$extras。${SetupAssistParser.summarizeForConfirm(next)}",
        )
        _uiState.update { it.copy(pendingConfirm = true) }
    }

    private fun offerConfirm(allowEmptyKey: Boolean) {
        val draft = _uiState.value.draft
        if (!draft.isReadyToConnect()) {
            append(fromUser = false, text = "还没有主机地址。")
            return
        }
        val kind = draft.resolvedKind()
        if (!allowEmptyKey &&
            (kind == AgentKind.HERMES || kind == AgentKind.HTTP_COMPAT || kind == AgentKind.GATEWAY) &&
            draft.apiKey.isNullOrBlank()
        ) {
            append(fromUser = false, text = "还需要 API Key；没有就回复「跳过」。")
            return
        }
        append(fromUser = false, text = SetupAssistParser.summarizeForConfirm(draft))
        _uiState.update { it.copy(pendingConfirm = true) }
    }

    private suspend fun tryConnect(allowEmptyKey: Boolean) {
        val draft = _uiState.value.draft
        val endpoint = draft.endpoint?.trim().orEmpty()
        if (endpoint.isEmpty()) {
            append(fromUser = false, text = "还没有主机地址。")
            return
        }
        val kind = draft.resolvedKind()
        if (!allowEmptyKey &&
            (kind == AgentKind.HERMES || kind == AgentKind.HTTP_COMPAT || kind == AgentKind.GATEWAY) &&
            draft.apiKey.isNullOrBlank()
        ) {
            append(fromUser = false, text = "还需要 API Key；没有就回复「跳过」。")
            return
        }

        _uiState.update { it.copy(busy = true, pendingConfirm = false) }
        append(fromUser = false, text = "正在测连…")

        val apiKey = ConnectionTester.sanitizeKey(draft.apiKey.orEmpty())
        val model = draft.model?.takeIf { it.isNotBlank() } ?: "default"
        val result = if (kind == AgentKind.LOCAL) {
            Result.success("本地编排")
        } else {
            connectionTester.test(endpoint = endpoint, apiKey = apiKey, model = model)
        }

        result.fold(
            onSuccess = { msg ->
                val name = draft.name?.takeIf { it.isNotBlank() } ?: kind.defaultName
                val profile = AgentProfile(
                    id = UUID.randomUUID().toString(),
                    kind = kind,
                    name = name,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    model = when {
                        model != "default" -> model
                        kind == AgentKind.GATEWAY -> "deepseek-chat"
                        else -> model
                    },
                    localToolsEnabled = kind != AgentKind.HTTP_COMPAT,
                )
                agentStore.saveAgent(profile, setCurrent = true)
                append(fromUser = false, text = "测连成功（$msg）。已保存「$name」，可以聊天了。")
                _uiState.update {
                    it.copy(busy = false, completedProfile = profile)
                }
            },
            onFailure = { err ->
                append(
                    fromUser = false,
                    text = "测连失败：${err.message ?: "未知错误"}。改一下再发我，或点手动配置。",
                )
                _uiState.update { it.copy(busy = false) }
            },
        )
    }

    private fun append(fromUser: Boolean, text: String) {
        _uiState.update {
            it.copy(messages = it.messages + AssistChatMessage(fromUser = fromUser, text = text))
        }
    }

    private fun isConfirm(text: String): Boolean {
        val t = text.trim()
        return t == "确认" || t == "好" || t == "可以" || t == "行" ||
            t.equals("ok", true) || t.equals("yes", true)
    }

    private fun isRefill(text: String): Boolean {
        val t = text.trim()
        return t == "重填" || t == "不对" || t == "重新" || t.equals("no", true)
    }

    companion object {
        fun factory(
            agentStore: AgentStore,
            endpointProbe: EndpointProbe,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SetupAssistViewModel::class.java)) {
                        return SetupAssistViewModel(agentStore, endpointProbe) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
    }
}
