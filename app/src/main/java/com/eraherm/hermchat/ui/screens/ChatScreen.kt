package com.eraherm.hermchat.ui.screens

import android.Manifest
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.InputMode
import com.eraherm.hermchat.data.local.ShortcutAction
import com.eraherm.hermchat.data.local.ShortcutDef
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.data.model.MessageRole
import com.eraherm.hermchat.service.VoiceEvent
import com.eraherm.hermchat.data.local.WakeEngineKind
import com.eraherm.hermchat.service.WakeWordService
import com.eraherm.hermchat.ui.components.AgentSwitcher
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.ConfirmCard
import com.eraherm.hermchat.ui.components.ConnectionStatus
import com.eraherm.hermchat.ui.components.MessageBubble
import com.eraherm.hermchat.ui.components.ShortcutBar
import com.eraherm.hermchat.ui.components.TypingBubble
import com.eraherm.hermchat.ui.theme.SoftGray
import com.eraherm.hermchat.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
            app,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wakeSettings by app.wakeSettingsStore.settings.collectAsStateWithLifecycle()
    val chatPrefs by app.chatPrefsStore.prefsFlow.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var stickToBottom by remember { mutableStateOf(true) }
    val textFocus = remember { FocusRequester() }
    val busy = uiState.isSending || uiState.isStreaming
    val canSend = draft.isNotBlank() && agent != null && !busy
    val shortcutsEnabled = agent != null && !busy
    val speechAvailable = remember {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    val voiceReady = speechAvailable || wakeSettings.engine == WakeEngineKind.OFFLINE
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.92f,
        label = "sendScale",
    )
    val displayMessages = remember(uiState.messages) { uiState.messages.asReversed() }
    val streamingId = remember(uiState.messages, uiState.isStreaming) {
        if (!uiState.isStreaming) null
        else uiState.messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
    }
    val showTyping = uiState.isSending && !uiState.isStreaming

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (index, offset) ->
            // reverseLayout：贴底 ≈ index 0 且 offset 很小
            stickToBottom = index <= 0 && offset <= 24
        }
    }

    fun scrollToLatest(animated: Boolean = false) {
        scope.launch {
            if (displayMessages.isEmpty() && !showTyping) return@launch
            if (animated) listState.animateScrollToItem(0) else listState.scrollToItem(0)
        }
    }

    val pttPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            WakeWordService.pushToTalk(context)
        } else {
            voiceStatus = "没有麦克风权限"
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
        if (!voiceReady) {
            voiceStatus = "本机暂无语音识别"
            return
        }
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
                stickToBottom = true
                viewModel.sendMessage(shortcut.text)
                scrollToLatest(animated = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        app.voiceEventBus.events.collect { event ->
            when (event) {
                is VoiceEvent.WakeDetected -> {
                    voiceStatus = "请说指令"
                    draft = ""
                }
                is VoiceEvent.Transcript -> {
                    voiceStatus = null
                    if (event.autoSend) {
                        // VoiceCloudBridge 统一发给云端，避免双发
                        draft = ""
                        stickToBottom = true
                        voiceStatus = "正在问助手…"
                    } else {
                        draft = event.text
                    }
                }
                is VoiceEvent.Status -> voiceStatus = event.message
                is VoiceEvent.Error -> voiceStatus = event.message
            }
        }
    }

    LaunchedEffect(voiceStatus) {
        val status = voiceStatus ?: return@LaunchedEffect
        if (status.contains("语音识别") || status.contains("没有麦克风")) {
            delay(2800)
            if (voiceStatus == status) voiceStatus = null
        }
    }

    LaunchedEffect(uiState.messages.size, showTyping) {
        if (stickToBottom) scrollToLatest(animated = true)
    }

    LaunchedEffect(uiState.messages.lastOrNull()?.content, uiState.isStreaming) {
        if (uiState.isStreaming && stickToBottom) {
            scrollToLatest(animated = false)
        }
    }

    LaunchedEffect(chatPrefs.inputMode) {
        if (chatPrefs.inputMode == InputMode.TEXT_FIRST) {
            runCatching { textFocus.requestFocus() }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onForeground()
        }
    }

    AtmosphereBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
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
                    onEditCurrent = onConfigureAgent,
                    modifier = Modifier.weight(1f),
                )
                ConnectionStatus(connected = uiState.connected)
                IconButton(
                    onClick = { viewModel.startNewChat() },
                    enabled = !busy,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddComment,
                        contentDescription = "新建对话",
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
                IconButton(onClick = onOpenChatPrefs) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "聊天设置",
                        tint = SoftGray,
                    )
                }
            }

            if (wakeSettings.enabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = voiceStatus?.takeIf { it.isNotBlank() } ?: "后台正在听「${wakeSettings.phrase}」",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            WakeWordService.stop(context)
                            voiceStatus = null
                        },
                    ) {
                        Text("停止听")
                    }
                }
            }

            AnimatedVisibility(
                visible = (!wakeSettings.enabled && voiceStatus != null) || uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    if (!wakeSettings.enabled) {
                        voiceStatus?.let { status ->
                            Text(
                                text = status,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
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
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showTyping) {
                    item(key = "typing") {
                        TypingBubble(bubbleStyle = chatPrefs.bubbleStyle)
                    }
                }
                items(displayMessages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        themeStyle = chatPrefs.themeStyle,
                        bubbleStyle = chatPrefs.bubbleStyle,
                        isStreaming = message.id == streamingId,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                ShortcutBar(
                    shortcuts = chatPrefs.shortcuts,
                    enabled = shortcutsEnabled,
                    onClick = ::applyShortcut,
                    onMoveLeft = { app.chatPrefsStore.moveShortcut(it.id, -1) },
                    onMoveRight = { app.chatPrefsStore.moveShortcut(it.id, 1) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DoubaoComposer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    inputMode = chatPrefs.inputMode,
                    agentName = agent?.name,
                    busy = busy,
                    canSend = canSend,
                    sendScale = sendScale,
                    showMic = voiceReady && chatPrefs.inputMode != InputMode.TEXT_FIRST,
                    textFocus = textFocus,
                    onMic = ::requestPushToTalk,
                    onSend = {
                        val text = draft
                        draft = ""
                        stickToBottom = true
                        viewModel.sendMessage(text)
                        scrollToLatest(animated = true)
                    },
                )
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

/** Doubao-like bottom capsule: mic · draft · send, lifted by IME. */
@Composable
private fun DoubaoComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    inputMode: InputMode,
    agentName: String?,
    busy: Boolean,
    canSend: Boolean,
    sendScale: Float,
    showMic: Boolean,
    textFocus: FocusRequester,
    onMic: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMic) {
                IconButton(
                    onClick = onMic,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "语音输入",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = when {
                            agentName != null -> "发消息给 $agentName"
                            else -> "发消息…"
                        },
                        color = SoftGray,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (inputMode == InputMode.TEXT_FIRST) {
                                Modifier.focusRequester(textFocus)
                            } else {
                                Modifier
                            },
                        ),
                    enabled = !busy,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSend() },
                    ),
                )
            }

            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(10.dp)
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
                        .size(44.dp)
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
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
