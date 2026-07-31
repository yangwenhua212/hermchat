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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.InputMode
import com.eraherm.hermchat.data.local.ShortcutAction
import com.eraherm.hermchat.data.local.ShortcutDef
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.service.VoiceEvent
import com.eraherm.hermchat.service.WakeWordService
import com.eraherm.hermchat.ui.components.AgentSwitcher
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.ConfirmCard
import com.eraherm.hermchat.ui.components.ConnectionStatus
import com.eraherm.hermchat.ui.components.MessageBubble
import com.eraherm.hermchat.ui.components.ShortcutBar
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
    onOpenChatPrefs: () -> Unit,
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
    val chatPrefs by app.chatPrefsStore.prefsFlow.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val textFocus = remember { FocusRequester() }
    val busy = uiState.isSending || uiState.isStreaming
    val canSend = draft.isNotBlank() && agent != null && !busy
    val shortcutsEnabled = agent != null && !busy
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.92f,
        label = "sendScale",
    )
    val micScale by animateFloatAsState(
        targetValue = when (chatPrefs.inputMode) {
            InputMode.VOICE_FIRST -> 1.12f
            InputMode.TEXT_FIRST -> 0.92f
            InputMode.MIXED -> 1f
        },
        label = "micScale",
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

    fun requestPushToTalk() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        pttPermissionLauncher.launch(permissions)
    }

    fun applyShortcut(shortcut: ShortcutDef) {
        when (shortcut.action) {
            ShortcutAction.INSERT -> draft = shortcut.text
            ShortcutAction.SEND -> {
                if (!shortcutsEnabled) return
                draft = ""
                viewModel.sendMessage(shortcut.text)
            }
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

    LaunchedEffect(chatPrefs.inputMode) {
        if (chatPrefs.inputMode == InputMode.TEXT_FIRST) {
            runCatching { textFocus.requestFocus() }
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
                IconButton(onClick = onOpenChatPrefs) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "聊天偏好",
                        tint = SoftGray,
                    )
                }
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

            ShortcutBar(
                shortcuts = chatPrefs.shortcuts,
                enabled = shortcutsEnabled,
                onClick = ::applyShortcut,
                onMoveLeft = { app.chatPrefsStore.moveShortcut(it.id, -1) },
                onMoveRight = { app.chatPrefsStore.moveShortcut(it.id, 1) },
                modifier = Modifier.fillMaxWidth(),
            )

            ComposerRow(
                draft = draft,
                onDraftChange = { draft = it },
                inputMode = chatPrefs.inputMode,
                wakeEnabled = wakeSettings.enabled,
                wakePhrase = wakeSettings.phrase,
                agentName = agent?.name,
                busy = busy,
                canSend = canSend,
                sendScale = sendScale,
                micScale = micScale,
                textFocus = textFocus,
                onMic = ::requestPushToTalk,
                onSend = {
                    val text = draft
                    draft = ""
                    viewModel.sendMessage(text)
                },
            )
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

@Composable
private fun ComposerRow(
    draft: String,
    onDraftChange: (String) -> Unit,
    inputMode: InputMode,
    wakeEnabled: Boolean,
    wakePhrase: String,
    agentName: String?,
    busy: Boolean,
    canSend: Boolean,
    sendScale: Float,
    micScale: Float,
    textFocus: FocusRequester,
    onMic: () -> Unit,
    onSend: () -> Unit,
) {
    val micTint = when (inputMode) {
        InputMode.VOICE_FIRST -> MaterialTheme.colorScheme.primary
        InputMode.TEXT_FIRST -> SoftGray
        InputMode.MIXED -> MaterialTheme.colorScheme.primary
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
        if (inputMode != InputMode.TEXT_FIRST) {
            MicButton(onClick = onMic, tint = micTint, scale = micScale)
        }

        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (inputMode == InputMode.TEXT_FIRST) {
                        Modifier.focusRequester(textFocus)
                    } else {
                        Modifier
                    },
                ),
            placeholder = {
                Text(
                    when {
                        wakeEnabled -> "说「$wakePhrase」或点麦克风…"
                        agentName != null -> "对 $agentName 说…"
                        else -> "输入消息…"
                    },
                    color = SoftGray,
                )
            },
            shape = RoundedCornerShape(18.dp),
            maxLines = 4,
            enabled = !busy,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )

        if (inputMode == InputMode.TEXT_FIRST) {
            MicButton(onClick = onMic, tint = micTint, scale = micScale)
        }

        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(12.dp)
                    .size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            IconButton(
                onClick = onSend,
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

@Composable
private fun MicButton(
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color,
    scale: Float,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.scale(scale),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "语音输入",
            tint = tint,
        )
    }
}
