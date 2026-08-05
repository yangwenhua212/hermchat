package com.eraherm.hermchat.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.AttachmentKind
import com.eraherm.hermchat.data.local.ChatAttachment
import com.eraherm.hermchat.data.local.ChatAttachmentStore
import com.eraherm.hermchat.data.local.InputMode
import com.eraherm.hermchat.data.local.ShortcutAction
import com.eraherm.hermchat.data.local.ShortcutDef
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.data.model.MessageRole
import com.eraherm.hermchat.data.network.AttachmentSupport
import com.eraherm.hermchat.service.VoiceEvent
import com.eraherm.hermchat.data.local.WakeEngineKind
import com.eraherm.hermchat.service.WakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eraherm.hermchat.ui.components.AgentBusyVisual
import com.eraherm.hermchat.ui.components.AgentSwitcher
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.AtmosphereBackdrop
import com.eraherm.hermchat.ui.components.ConfirmCard
import com.eraherm.hermchat.ui.components.ConnectionStatus
import com.eraherm.hermchat.ui.components.ConversationHistoryMenu
import com.eraherm.hermchat.ui.components.MessageBubble
import com.eraherm.hermchat.ui.components.ShortcutBar
import com.eraherm.hermchat.ui.components.TypingBubble
import com.eraherm.hermchat.ui.theme.SoftGray
import com.eraherm.hermchat.viewmodel.ChatViewModel
import com.eraherm.hermchat.viewmodel.LoopStep
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
    onOpenLibrary: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as HermChatApp
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            app.messageRepository,
            app.conversationRepository,
            app.agentStore,
            app.toolRegistry,
            app,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val wakeSettings by app.wakeSettingsStore.settings.collectAsStateWithLifecycle()
    val chatPrefs by app.chatPrefsStore.prefsFlow.collectAsStateWithLifecycle()
    val speakingMessageId by app.replySpeaker.speakingMessageId.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var draftAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var stickToBottom by remember { mutableStateOf(true) }
    val textFocus = remember { FocusRequester() }
    val busy = uiState.isSending || uiState.isStreaming || uiState.toolExecuting
    val canSend = (draft.isNotBlank() || draftAttachment != null) &&
        agent != null &&
        !uiState.isSending &&
        !uiState.isStreaming
    val shortcutsEnabled = agent != null && !uiState.isSending
    val attachmentStore = remember { ChatAttachmentStore(context) }
    val sharePending by app.shareInbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(sharePending) {
        if (sharePending == null) return@LaunchedEffect
        val pending = app.shareInbox.consume() ?: return@LaunchedEffect
        pending.text?.takeIf { it.isNotBlank() }?.let { shared ->
            draft = if (draft.isBlank()) shared else "$draft\n$shared"
        }
        val uri = pending.uri ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { attachmentStore.importUri(uri) }
        result.onSuccess { attachment ->
            if (!AttachmentSupport.canSend(agent, attachment.kind)) {
                voiceStatus = AttachmentSupport.unsupportedStatus(agent, attachment.kind)
            } else {
                draftAttachment = attachment
            }
        }.onFailure {
            voiceStatus = "无法导入分享内容"
        }
    }
    val pickAttachment = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { attachmentStore.importUri(uri) }
            result.onSuccess { attachment ->
                if (!AttachmentSupport.canSend(agent, attachment.kind)) {
                    voiceStatus = AttachmentSupport.unsupportedStatus(agent, attachment.kind)
                } else {
                    draftAttachment = attachment
                }
            }.onFailure { voiceStatus = it.message?.take(24) ?: "选文件失败" }
        }
    }
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
    // 机器人侧思考中：忙碌且尚未有助手流式正文时显示圆点气泡
    val hasStreamingText = uiState.isStreaming &&
        uiState.messages.lastOrNull()?.let {
            it.role == MessageRole.ASSISTANT && it.content.isNotBlank()
        } == true
    val loopLabel = remember(uiState.loopStep) {
        when (val step = uiState.loopStep) {
            LoopStep.Idle, is LoopStep.Error, is LoopStep.Finished -> null
            else -> step.label()
        }
    }
    val showTyping = (busy || uiState.loopStep is LoopStep.Observing) &&
        !hasStreamingText &&
        uiState.pendingToolCall == null

    fun scrollToLatest(animated: Boolean = false) {
        scope.launch {
            if (displayMessages.isEmpty() && !showTyping) return@launch
            if (animated) listState.animateScrollToItem(0) else listState.scrollToItem(0)
        }
    }

    // 滚动：贴底检测 + 自动追底
    LaunchedEffect(Unit) {
        launch {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }.distinctUntilChanged().collect { (index, offset) ->
                stickToBottom = index <= 0 && offset <= 24
            }
        }
        launch {
            snapshotFlow {
                Triple(uiState.messages.size, showTyping, uiState.loopStep)
            }
                .distinctUntilChanged()
                .collect { if (stickToBottom) scrollToLatest(animated = true) }
        }
        launch {
            snapshotFlow { (uiState.messages.lastOrNull()?.content ?: "") to uiState.isStreaming }
                .distinctUntilChanged()
                .collect { (_, streaming) ->
                    if (streaming && stickToBottom) scrollToLatest(animated = false)
                }
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

    val toolPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.all { it }
        if (granted) {
            viewModel.confirmPendingTool()
        } else {
            voiceStatus = "缺少权限，无法完成操作"
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

    // ──────────────────────────────────────────────
    // 语音事件 + 生命周期（2 合 1）
    // ──────────────────────────────────────────────
    LaunchedEffect(Unit) {
        // 语音事件收集
        launch {
            app.voiceEventBus.events.collect { event ->
                when (event) {
                    is VoiceEvent.WakeDetected -> {
                        voiceStatus = "请说指令"
                        draft = ""
                    }
                    is VoiceEvent.Transcript -> {
                        voiceStatus = null
                        if (event.autoSend) {
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
        // 顶栏状态一律短显后消失，避免长文案占聊天区
        launch {
            snapshotFlow { voiceStatus }.collect { status ->
                val s = status ?: return@collect
                delay(2200)
                if (voiceStatus == s) voiceStatus = null
            }
        }
        // 朗读失败（异步）：只收需要展示的事件，避免粘住旧的「云端 404」
        launch {
            app.replySpeaker.userErrors.collect { err ->
                voiceStatus = err
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onForeground()
            if (wakeSettings.enabled) {
                WakeWordService.setInAppDirectListen(context, true)
            }
        }
    }
    DisposableEffect(lifecycleOwner, wakeSettings.enabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP ->
                    WakeWordService.setInAppDirectListen(context, false)
                Lifecycle.Event.ON_START ->
                    if (wakeSettings.enabled) {
                        WakeWordService.setInAppDirectListen(context, true)
                    }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            WakeWordService.setInAppDirectListen(context, false)
        }
    }

    // ──────────────────────────────────────────────
    // TTS 朗读 + 键盘焦点
    // ──────────────────────────────────────────────
    LaunchedEffect(chatPrefs.inputMode, chatPrefs.autoSpeakReplies) {
        if (chatPrefs.inputMode == InputMode.TEXT_FIRST) {
            runCatching { textFocus.requestFocus() }
        }
        if (chatPrefs.autoSpeakReplies) {
            launch {
                var streamId: String? = null
                var primed = false
                snapshotFlow {
                    val last = uiState.messages.lastOrNull()
                    Triple(
                        uiState.isStreaming,
                        last?.takeIf { it.role == MessageRole.ASSISTANT },
                        last?.content.orEmpty(),
                    )
                }.distinctUntilChanged().collect { (streaming, last, content) ->
                    // 进页/重启收集器：已有助手气泡一律视为已读过，禁止「回来又自动读一遍」
                    if (!primed) {
                        val liveId = if (streaming) last?.id else null
                        uiState.messages
                            .asSequence()
                            .filter { it.role == MessageRole.ASSISTANT }
                            .filter { !it.id.startsWith("welcome-") }
                            .filter { it.id != liveId }
                            .forEach { app.replySpeaker.noteAutoHandled(it.id) }
                        primed = true
                    }
                    if (streaming && last != null &&
                        content.isNotBlank() &&
                        !last.id.startsWith("welcome-") &&
                        !app.replySpeaker.isAutoHandled(last.id)
                    ) {
                        if (streamId != last.id) {
                            streamId = last.id
                            app.replySpeaker.beginStreamSpeak(last.id)
                        }
                        app.replySpeaker.onStreamText(last.id, content)
                    } else if (!streaming && streamId != null) {
                        val id = streamId!!
                        streamId = null
                        app.replySpeaker.endStreamSpeak(id)
                        app.replySpeaker.noteAutoHandled(id)
                    } else if (!streaming && last != null &&
                        content.isNotBlank() &&
                        !last.id.startsWith("welcome-") &&
                        !app.replySpeaker.isAutoHandled(last.id)
                    ) {
                        // 非流式整段落盘：只读这一次，立刻记入已处理
                        app.replySpeaker.noteAutoHandled(last.id)
                        app.replySpeaker.speak(content, last.id)
                    }
                }
            }
        } else {
            launch {
                snapshotFlow { uiState.isStreaming }.collect { streaming ->
                    if (streaming) app.replySpeaker.stop()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { app.replySpeaker.stop() }
    }

    AtmosphereBackground(
        themeStyle = chatPrefs.themeStyle,
        // 壁纸只铺消息区；顶栏 / 输入区继续用主题渐变
        imagePath = null,
    ) {
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
                    onManageLibrary = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
                ConnectionStatus(connected = uiState.connected)
                ConversationHistoryMenu(
                    conversations = uiState.conversations,
                    activeId = uiState.activeConversationId,
                    enabled = !busy,
                    onOpen = { viewModel.openConversation(it) },
                    onDelete = { viewModel.deleteConversation(it) },
                )
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

            // 状态提示：错误 / 超步数一切 / Loop 中间态 / 语音状态短显
            AnimatedVisibility(
                visible = uiState.error != null ||
                    uiState.loopEscalate != null ||
                    voiceStatus != null ||
                    (loopLabel != null && hasStreamingText),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                    val escalate = uiState.loopEscalate
                    if (escalate != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = uiState.error ?: "步骤较多",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    if (escalate.hasTarget) {
                                        viewModel.switchToLoopEscalateTarget()
                                    } else {
                                        viewModel.dismissLoopEscalate()
                                        onAddAgent()
                                    }
                                },
                            ) {
                                Text(escalate.actionLabel)
                            }
                        }
                    } else {
                        uiState.error?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                    if (loopLabel != null && hasStreamingText && escalate == null && uiState.error == null) {
                        Text(
                            text = loopLabel,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    voiceStatus?.let { status ->
                        Text(
                            text = status,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                chatPrefs.resolvedImagePath()?.let { path ->
                    AtmosphereBackdrop(
                        modifier = Modifier.fillMaxSize(),
                        themeStyle = chatPrefs.themeStyle,
                        imagePath = path,
                    )
                }
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showTyping) {
                        item(key = "typing") {
                            TypingBubble(
                                bubbleStyle = chatPrefs.bubbleStyle,
                                visual = if (
                                    uiState.toolExecuting ||
                                    uiState.loopStep is LoopStep.Executing ||
                                    uiState.loopStep is LoopStep.Observing
                                ) {
                                    AgentBusyVisual.WORKING
                                } else {
                                    AgentBusyVisual.THINKING
                                },
                                label = loopLabel,
                            )
                        }
                    }
                    items(displayMessages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            themeStyle = chatPrefs.themeStyle,
                            bubbleStyle = chatPrefs.bubbleStyle,
                            isStreaming = message.id == streamingId,
                            isSpeaking = speakingMessageId == message.id,
                            onSpeakClick = if (message.role == MessageRole.ASSISTANT) {
                                {
                                    app.replySpeaker.toggle(message.content, message.id)
                                }
                            } else {
                                null
                            },
                        )
                    }
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
                draftAttachment?.let { attachment ->
                    val preview = remember(attachment.path, attachment.kind) {
                        if (attachment.kind != AttachmentKind.IMAGE) return@remember null
                        runCatching {
                            BitmapFactory.Options().run {
                                inSampleSize = 4
                                BitmapFactory.decodeFile(attachment.path, this)?.asImageBitmap()
                            }
                        }.getOrNull()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (preview != null) {
                            Image(
                                bitmap = preview,
                                contentDescription = "待发图片",
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(
                            text = when (attachment.kind) {
                                AttachmentKind.PDF -> "PDF · ${attachment.name}"
                                AttachmentKind.TEXT -> "文件 · ${attachment.name}"
                                AttachmentKind.IMAGE ->
                                    if (preview != null) attachment.name else "图片 · ${attachment.name}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { draftAttachment = null }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "移除附件",
                                tint = SoftGray,
                            )
                        }
                    }
                }
                DoubaoComposer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    inputMode = chatPrefs.inputMode,
                    agentName = agent?.name,
                    canSend = canSend,
                    sendScale = sendScale,
                    showMic = voiceReady && chatPrefs.inputMode != InputMode.TEXT_FIRST,
                    textFocus = textFocus,
                    onAttach = {
                        pickAttachment.launch(ChatAttachmentStore.openDocumentMimeTypes())
                    },
                    onMic = ::requestPushToTalk,
                    onSend = {
                        val text = draft
                        val attachment = draftAttachment
                        draft = ""
                        draftAttachment = null
                        stickToBottom = true
                        viewModel.sendMessage(
                            text = text,
                            attachmentPath = attachment?.path,
                            attachmentMime = attachment?.mime,
                            attachmentName = attachment?.name,
                        )
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
                    toolPermissionLauncher.launch(permissions)
                }
            },
            onDeny = { viewModel.denyPendingTool() },
        )
    }
}

/** Doubao-like bottom capsule: mic · image · draft · send, lifted by IME. */
@Composable
private fun DoubaoComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    inputMode: InputMode,
    agentName: String?,
    canSend: Boolean,
    sendScale: Float,
    showMic: Boolean,
    textFocus: FocusRequester,
    onAttach: () -> Unit,
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
            IconButton(
                onClick = onAttach,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = "添加附件",
                    tint = SoftGray,
                )
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
                    enabled = true,
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
