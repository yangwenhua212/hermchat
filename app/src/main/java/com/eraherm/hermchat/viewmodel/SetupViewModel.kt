package com.eraherm.hermchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eraherm.hermchat.data.local.AgentStore
import com.eraherm.hermchat.data.local.DeviceCapability
import com.eraherm.hermchat.data.local.LocalModelStore
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.data.network.AgentConfigImport
import com.eraherm.hermchat.data.network.ConnectionTester
import com.eraherm.hermchat.data.network.EndpointProbe
import com.eraherm.hermchat.data.network.ProbeHit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val step: Int = 1,
    val kind: AgentKind = AgentKind.WEBSOCKET,
    val endpoint: String = AgentKind.WEBSOCKET.defaultEndpoint,
    val name: String = AgentKind.WEBSOCKET.defaultName,
    val apiKey: String = "",
    val model: String = "default",
    val testing: Boolean = false,
    val testPassed: Boolean = false,
    val testMessage: String? = null,
    val probing: Boolean = false,
    val probeHits: List<ProbeHit> = emptyList(),
    val probeMessage: String? = null,
    val importHint: String? = null,
    val saving: Boolean = false,
    val downloadingModel: Boolean = false,
    val downloadProgress: Float = 0f,
    val modelReady: Boolean = false,
    val error: String? = null,
    val completedProfile: AgentProfile? = null,
)

class SetupViewModel(
    private val agentStore: AgentStore,
    private val endpointProbe: EndpointProbe,
    private val localModelStore: LocalModelStore,
    private val appContext: android.content.Context,
    private val connectionTester: ConnectionTester = ConnectionTester(),
    initial: AgentProfile? = null,
) : ViewModel() {

    private val editingId: String? = initial?.id

    private val _uiState = MutableStateFlow(
        if (initial != null) {
            SetupUiState(
                step = 1,
                kind = initial.kind,
                endpoint = initial.endpoint,
                name = initial.name,
                apiKey = initial.apiKey,
                model = initial.model,
                modelReady = localModelStore.isReady(
                    initial.model.takeIf { it.isNotBlank() && it != "default" }
                        ?: LocalModelStore.DEFAULT_MODEL_ID,
                ),
            )
        } else {
            SetupUiState(
                modelReady = localModelStore.isReady(),
            )
        },
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun selectKind(kind: AgentKind) {
        _uiState.update {
            it.copy(
                kind = kind,
                endpoint = kind.defaultEndpoint,
                model = if (kind == AgentKind.LOCAL) {
                    LocalModelStore.DEFAULT_MODEL_ID
                } else if (it.model == LocalModelStore.DEFAULT_MODEL_ID) {
                    "default"
                } else {
                    it.model
                },
                name = if (it.name == it.kind.defaultName || it.name.isBlank()) {
                    kind.defaultName
                } else {
                    it.name
                },
                testPassed = false,
                testMessage = null,
                probeHits = emptyList(),
                probeMessage = null,
                modelReady = if (kind == AgentKind.LOCAL) localModelStore.isReady() else it.modelReady,
                error = null,
            )
        }
    }

    fun updateEndpoint(value: String) {
        _uiState.update {
            it.copy(
                endpoint = value,
                testPassed = false,
                testMessage = null,
                error = null,
            )
        }
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value, error = null) }
    }

    fun updateApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value, error = null) }
    }

    fun updateModel(value: String) {
        _uiState.update { it.copy(model = value, error = null) }
    }

    fun applyImport(raw: String) {
        val parsed = AgentConfigImport.parse(raw)
        parsed.fold(
            onSuccess = { cfg ->
                val kind = cfg.kind ?: _uiState.value.kind
                _uiState.update {
                    it.copy(
                        step = 2,
                        kind = kind,
                        endpoint = cfg.endpoint,
                        name = cfg.name?.takeIf { n -> n.isNotBlank() }
                            ?: if (it.name.isBlank() || it.name == it.kind.defaultName) {
                                kind.defaultName
                            } else {
                                it.name
                            },
                        apiKey = cfg.apiKey ?: it.apiKey,
                        model = cfg.model?.takeIf { m -> m.isNotBlank() } ?: it.model,
                        testPassed = false,
                        testMessage = null,
                        probeHits = emptyList(),
                        probeMessage = null,
                        importHint = "已从二维码/粘贴填入，建议点「测试」确认",
                        error = null,
                    )
                }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(error = "导入失败：${err.message ?: "无法识别"}")
                }
            },
        )
    }

    fun clearImportHint() {
        _uiState.update { it.copy(importHint = null) }
    }

    fun probeEndpoints() {
        val kind = _uiState.value.kind
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    probing = true,
                    probeHits = emptyList(),
                    probeMessage = null,
                    error = null,
                )
            }
            val hits = runCatching { endpointProbe.discover(kind) }
                .getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    probing = false,
                    probeHits = hits,
                    probeMessage = if (hits.isEmpty()) {
                        "附近未发现可达端点。真机请确认与电脑同一 Wi‑Fi，或手动填局域网 IP。"
                    } else {
                        "发现 ${hits.size} 个可达地址，点选填入"
                    },
                    endpoint = hits.firstOrNull()?.endpoint ?: it.endpoint,
                    testPassed = hits.isNotEmpty(),
                    testMessage = hits.firstOrNull()?.detail,
                )
            }
        }
    }

    fun useProbeHit(hit: ProbeHit) {
        _uiState.update {
            it.copy(
                endpoint = hit.endpoint,
                testPassed = true,
                testMessage = hit.detail,
                importHint = null,
                error = null,
            )
        }
    }

    fun nextStep() {
        val state = _uiState.value
        when (state.step) {
            1 -> _uiState.update { it.copy(step = 2, error = null) }
            2 -> goToNameStep(requireTest = true)
        }
    }

    fun previousStep() {
        _uiState.update {
            if (it.step > 1) it.copy(step = it.step - 1, error = null) else it
        }
    }

    fun testConnection() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(testing = true, testMessage = null, testPassed = false, error = null)
            }
            if (state.kind == AgentKind.LOCAL) {
                val ready = localModelStore.isReady(
                    state.model.ifBlank { LocalModelStore.DEFAULT_MODEL_ID },
                )
                _uiState.update {
                    it.copy(
                        testing = false,
                        testPassed = true,
                        testMessage = if (ready) "本地模型已就绪" else "本地编排已就绪",
                        modelReady = ready,
                    )
                }
                return@launch
            }
            val result = connectionTester.test(
                endpoint = state.endpoint,
                apiKey = state.apiKey,
                model = state.model,
            )
            _uiState.update {
                result.fold(
                    onSuccess = { msg ->
                        it.copy(
                            testing = false,
                            testPassed = true,
                            testMessage = msg,
                        )
                    },
                    onFailure = { err ->
                        val msg = friendlyConnectError(err)
                        it.copy(
                            testing = false,
                            testPassed = false,
                            testMessage = msg,
                            error = msg,
                        )
                    },
                )
            }
        }
    }

    fun downloadLocalModel() {
        val state = _uiState.value
        if (!DeviceCapability.canRunLocalLlm(appContext)) {
            _uiState.update {
                it.copy(error = "该设备内存不足，不支持本地大模型")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(downloadingModel = true, downloadProgress = 0f, error = null)
            }
            val modelId = state.model.ifBlank { LocalModelStore.DEFAULT_MODEL_ID }
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                localModelStore.ensureInstalled(
                    modelId = modelId,
                    hfToken = state.apiKey,
                ) { progress ->
                    _uiState.update { ui -> ui.copy(downloadProgress = progress) }
                }
            }
            _uiState.update { ui ->
                result.fold(
                    onSuccess = {
                        ui.copy(
                            downloadingModel = false,
                            downloadProgress = 1f,
                            modelReady = true,
                            testPassed = true,
                            testMessage = "本地模型已就绪",
                        )
                    },
                    onFailure = { err ->
                        ui.copy(
                            downloadingModel = false,
                            modelReady = false,
                            error = err.message ?: "下载失败",
                        )
                    },
                )
            }
        }
    }

    fun skipTestAndContinue() {
        goToNameStep(requireTest = false)
    }

    fun finish() {
        val state = _uiState.value
        val name = state.name.trim().ifEmpty { state.kind.defaultName }
        val endpoint = if (state.kind == AgentKind.LOCAL) {
            AgentKind.LOCAL.defaultEndpoint
        } else {
            state.endpoint.trim()
        }
        if (endpoint.isBlank()) {
            _uiState.update { it.copy(error = "地址不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            val profile = AgentProfile(
                id = editingId ?: java.util.UUID.randomUUID().toString(),
                kind = state.kind,
                name = name,
                endpoint = endpoint,
                apiKey = state.apiKey.let { ConnectionTester.sanitizeKey(it) },
                model = state.model.trim().ifBlank {
                    if (state.kind == AgentKind.LOCAL) LocalModelStore.DEFAULT_MODEL_ID else "default"
                },
            )
            agentStore.saveAgent(profile, setCurrent = true)
            _uiState.update {
                it.copy(saving = false, completedProfile = profile)
            }
        }
    }

    private fun goToNameStep(requireTest: Boolean) {
        val state = _uiState.value
        if (state.kind != AgentKind.LOCAL && state.endpoint.isBlank()) {
            _uiState.update { it.copy(error = "请先填写地址") }
            return
        }
        if (requireTest && !state.testPassed) {
            _uiState.update { it.copy(error = "请先点「测试」确认连通，或选择跳过") }
            return
        }
        _uiState.update { it.copy(step = 3, error = null) }
    }

    companion object {
        fun factory(
            agentStore: AgentStore,
            endpointProbe: EndpointProbe,
            localModelStore: LocalModelStore,
            appContext: android.content.Context,
            initial: AgentProfile? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SetupViewModel::class.java)) {
                        return SetupViewModel(
                            agentStore = agentStore,
                            endpointProbe = endpointProbe,
                            localModelStore = localModelStore,
                            appContext = appContext.applicationContext,
                            initial = initial,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }

        private fun friendlyConnectError(err: Throwable): String {
            val msg = err.message.orEmpty()
            return when {
                msg.contains("服务可达") ||
                    msg.contains("路径不存在") ||
                    msg.contains("请求被拒绝") ||
                    msg.contains("模型名") ||
                    msg.startsWith("HTTP ") -> msg

                err is java.net.SocketTimeoutException ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("timed out", ignoreCase = true) ->
                    "连接超时，请检查地址与端口"

                err is java.net.ConnectException ||
                    msg.contains("Failed to connect", ignoreCase = true) ||
                    msg.contains("Connection refused", ignoreCase = true) ->
                    "无法连接，请检查地址与网络"

                msg.contains("CLEARTEXT", ignoreCase = true) ->
                    "明文 HTTP 被系统拦截"

                msg.isBlank() -> "连接失败"
                else -> msg.take(120)
            }
        }
    }
}
