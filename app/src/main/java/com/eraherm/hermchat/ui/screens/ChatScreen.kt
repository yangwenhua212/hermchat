package com.eraherm.hermchat.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.service.VoiceEvent
import com.eraherm.hermchat.service.WakeWordService
import com.eraherm.hermchat.ui.components.AgentSwitcher
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.ConfirmCard
import com.eraherm.hermchat.ui.components.ConnectionStatus
import com.eraherm.hermchat.ui.components.MessageBubble
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray
import com.eraherm.hermchat.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    agent: AgentProfile?,
    agents: List<AgentProfile>,
    onSelectAgent: (AgentProfile) -> Unit,
    onAddAgent: () -> Unit,
    onConfigureAgent: () -> Unit,
    onOpenWakeSetup: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HermChatApp
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            app.messageRepository,
            app.agentStore,
            app.toolRegistry,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wakeSettings by app.wakeSettingsStore.settings.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val canSend = draft.isNotBlank() && agent != null && !uiState.isSending && !uiState.isStreaming
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.92f,
        label = "sendScale",
    )

    val pttPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            WakeWordService.pushToTalk(context)
        } else {
            voiceStatus = "没有麦克风权限就无法语音输入"
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.all { it }
        if (granted) {
            viewModel.confirmPendingTool()
        } else {
            voiceStatus = "没有日历权限，无法创建日程"
            viewModel.denyPendingTool()
        }
    }

    LaunchedEffect(Unit) {
        app.voiceEventBus.events.collect { event ->
            when (event) {
                is VoiceEvent.WakeDetected -> {
                    voiceStatus = "在呢 · 听到「${event.phrase}」"
                    draft = ""
                }
                is VoiceEvent.Transcript -> {
                    voiceStatus = null
                    if (event.autoSend) {
                        draft = ""
                        viewModel.sendMessage(event.text)
                    } else {
                        draft = event.text
                    }
                }
                is VoiceEvent.Status -> voiceStatus = event.message
                is VoiceEvent.Error -> voiceStatus = event.message
            }
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    AtmosphereBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AgentSwitcher(
                    agents = agents,
                    current = agent,
                    connected = uiState.connected,
                    onSelect = onSelectAgent,
                    onAdd = onAddAgent,
                    modifier = Modifier.weight(1f),
                )
                ConnectionStatus(connected = uiState.connected)
                IconButton(onClick = onOpenWakeSetup) {
                    Icon(
                        imageVector = Icons.Filled.RecordVoiceOver,
                        contentDescription = "唤醒词设置",
                        tint = if (wakeSettings.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            SoftGray
                        },
                    )
                }
                IconButton(onClick = onConfigureAgent) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "编辑当前 Agent",
                        tint = SoftGray,
                    )
                }
            }

            AnimatedVisibility(
                visible = voiceStatus != null || uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    voiceStatus?.let { status ->
                        Text(
                            text = status,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
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

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .border(1.dp, Line.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        val permissions = buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= 33) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.toTypedArray()
                        pttPermissionLauncher.launch(permissions)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "语音输入",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when {
                                wakeSettings.enabled -> "说「${wakeSettings.phrase}」或点麦克风…"
                                agent != null -> "对 ${agent.name} 说…"
                                else -> "输入消息…"
                            },
                            color = SoftGray,
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 4,
                    enabled = !uiState.isSending && !uiState.isStreaming,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                if (uiState.isSending || uiState.isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButton(
                        onClick = {
                            val text = draft
                            draft = ""
                            viewModel.sendMessage(text)
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .scale(sendScale)
                            .padding(end = 2.dp)
                            .background(
                                color = if (canSend) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (canSend) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                SoftGray
                            },
                        )
                    }
                }
            }
        }
    }

    uiState.pendingToolCall?.let { call ->
        ConfirmCard(
            toolCall = call,
            busy = uiState.toolExecuting,
            onAllow = {
                val permissions = viewModel.permissionsForPendingTool()
                if (permissions.isEmpty()) {
                    viewModel.confirmPendingTool()
                } else {
                    calendarPermissionLauncher.launch(permissions)
                }
            },
            onDeny = viewModel::denyPendingTool,
        )
    }
}
