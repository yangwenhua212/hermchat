package com.eraherm.hermchat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray
import com.eraherm.hermchat.viewmodel.SetupViewModel

@Composable
fun AgentSetupScreen(
    editing: AgentProfile? = null,
    onFinished: (AgentProfile) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as HermChatApp
    val viewModel: SetupViewModel = viewModel(
        key = editing?.id ?: "new-agent",
        factory = SetupViewModel.factory(app.agentStore, editing),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.completedProfile) {
        uiState.completedProfile?.let(onFinished)
    }

    AtmosphereBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            BrandMark(
                subtitle = "三步配好你的 Agent · 全程在 App 内",
                compact = true,
            )
            Spacer(modifier = Modifier.height(18.dp))
            StepHeader(current = uiState.step)
            Spacer(modifier = Modifier.height(18.dp))

            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "setupStep",
                modifier = Modifier.weight(1f),
            ) { step ->
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (step) {
                        1 -> StepSelectKind(
                            selected = uiState.kind,
                            onSelect = viewModel::selectKind,
                        )
                        2 -> StepEndpoint(
                            endpoint = uiState.endpoint,
                            testing = uiState.testing,
                            testPassed = uiState.testPassed,
                            testMessage = uiState.testMessage,
                            onEndpointChange = viewModel::updateEndpoint,
                            onTest = viewModel::testConnection,
                            onUsePreset = { kind -> viewModel.selectKind(kind) },
                            onSkipTest = viewModel::skipTestAndContinue,
                            kind = uiState.kind,
                        )
                        else -> StepName(
                            name = uiState.name,
                            onNameChange = viewModel::updateName,
                        )
                    }
                    uiState.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uiState.step > 1) {
                    OutlinedButton(
                        onClick = viewModel::previousStep,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("上一步") }
                } else if (onCancel != null) {
                    TextButton(onClick = onCancel) { Text("取消") }
                }

                Button(
                    onClick = {
                        if (uiState.step < 3) viewModel.nextStep() else viewModel.finish()
                    },
                    enabled = !uiState.testing && !uiState.saving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        when {
                            uiState.saving -> "保存中…"
                            uiState.step < 3 -> "下一步"
                            else -> "完成并开始聊天"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeader(current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            1 to "选类型",
            2 to "填地址",
            3 to "起名字",
        ).forEach { (step, label) ->
            val active = step == current
            val done = step < current
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = when {
                    active -> MaterialTheme.colorScheme.primary
                    done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surface
                },
                border = if (!active && !done) {
                    BorderStroke(1.dp, Line)
                } else {
                    null
                },
            ) {
                Text(
                    text = "$step · $label",
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        active -> MaterialTheme.colorScheme.onPrimary
                        done -> MaterialTheme.colorScheme.primary
                        else -> SoftGray
                    },
                )
            }
        }
    }
}

@Composable
private fun StepSelectKind(
    selected: AgentKind,
    onSelect: (AgentKind) -> Unit,
) {
    Text(
        text = "Step 1：选择你的 Agent 类型",
        style = MaterialTheme.typography.bodyLarge,
    )
    AgentKind.entries.forEach { kind ->
        val selectedKind = kind == selected
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selectedKind,
                    onClick = { onSelect(kind) },
                    role = Role.RadioButton,
                ),
            shape = RoundedCornerShape(16.dp),
            color = if (selectedKind) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (selectedKind) {
                    MaterialTheme.colorScheme.primary
                } else {
                    SoftGray.copy(alpha = 0.35f)
                },
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = when (kind) {
                        AgentKind.WEBSOCKET -> "WebSocket 端点（模拟器访问本机用 10.0.2.2）"
                        AgentKind.HTTP_COMPAT -> "OpenAI 兼容 HTTP（如 /v1/chat/completions）"
                        AgentKind.CUSTOM -> "任意 ws:// 或 http(s):// 地址"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StepEndpoint(
    kind: AgentKind,
    endpoint: String,
    testing: Boolean,
    testPassed: Boolean,
    testMessage: String?,
    onEndpointChange: (String) -> Unit,
    onTest: () -> Unit,
    onUsePreset: (AgentKind) -> Unit,
    onSkipTest: () -> Unit,
) {
    Text(
        text = "Step 2：填地址（就这一行）",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = "真机请改成电脑局域网 IP；模拟器访问本机用 10.0.2.2",
        style = MaterialTheme.typography.bodyMedium,
        color = SoftGray,
    )
    OutlinedTextField(
        value = endpoint,
        onValueChange = onEndpointChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Agent 地址") },
        shape = RoundedCornerShape(16.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { onUsePreset(kind) },
            modifier = Modifier.weight(1f),
        ) {
            Text("恢复预设")
        }
        Button(
            onClick = onTest,
            enabled = endpoint.isNotBlank() && !testing,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (testing) "测试中…" else "测试")
        }
    }
    testMessage?.let { message ->
        Text(
            text = if (testPassed) "测连成功 · $message" else "测连失败 · $message",
            color = if (testPassed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (!testPassed) {
        TextButton(onClick = onSkipTest) {
            Text("Agent 暂未开机？跳过测连")
        }
    }
}

@Composable
private fun StepName(
    name: String,
    onNameChange: (String) -> Unit,
) {
    Text(
        text = "Step 3：起个名字（可选）",
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("显示名称") },
        placeholder = { Text("我的助手") },
        shape = RoundedCornerShape(16.dp),
    )
    Text(
        text = "之后可在顶栏切换多个 Agent。",
        style = MaterialTheme.typography.bodyMedium,
        color = SoftGray,
    )
}
