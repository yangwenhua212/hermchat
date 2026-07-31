package com.eraherm.hermchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eraherm.hermchat.data.local.AgentStore
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
    val testing: Boolean = false,
    val testPassed: Boolean = false,
    val testMessage: String? = null,
    val probing: Boolean = false,
    val probeHits: List<ProbeHit> = emptyList(),
    val probeMessage: String? = null,
    val importHint: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val completedProfile: AgentProfile? = null,
)

class SetupViewModel(
    private val agentStore: AgentStore,
    private val endpointProbe: EndpointProbe,
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
            )
        } else {
            SetupUiState()
        },
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun selectKind(kind: AgentKind) {
        _uiState.update {
            it.copy(
                kind = kind,
                endpoint = kind.defaultEndpoint,
                name = if (it.name == it.kind.defaultName || it.name.isBlank()) {
                    kind.defaultName
                } else {
                    it.name
                },
                testPassed = false,
                testMessage = null,
                probeHits = emptyList(),
                probeMessage = null,
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
        val endpoint = _uiState.value.endpoint
        viewModelScope.launch {
            _uiState.update {
                it.copy(testing = true, testMessage = null, testPassed = false, error = null)
            }
            val result = connectionTester.test(endpoint)
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
                        it.copy(
                            testing = false,
                            testPassed = false,
                            testMessage = err.message ?: "连接失败",
                            error = "测连失败：${err.message ?: "未知错误"}",
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
        if (state.endpoint.isBlank()) {
            _uiState.update { it.copy(error = "地址不能为空") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            val profile = AgentProfile(
                id = editingId ?: java.util.UUID.randomUUID().toString(),
                kind = state.kind,
                name = name,
                endpoint = state.endpoint.trim(),
            )
            agentStore.saveAgent(profile, setCurrent = true)
            _uiState.update {
                it.copy(saving = false, completedProfile = profile)
            }
        }
    }

    private fun goToNameStep(requireTest: Boolean) {
        val state = _uiState.value
        if (state.endpoint.isBlank()) {
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
            initial: AgentProfile? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SetupViewModel::class.java)) {
                        return SetupViewModel(
                            agentStore = agentStore,
                            endpointProbe = endpointProbe,
                            initial = initial,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
    }
}
