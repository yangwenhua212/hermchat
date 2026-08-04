package com.eraherm.hermchat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.BubbleStyle
import com.eraherm.hermchat.data.local.ChatBackgroundMode
import com.eraherm.hermchat.data.local.ChatPrefs
import com.eraherm.hermchat.data.local.ChatPrefsStore
import com.eraherm.hermchat.data.local.ChatThemeStyle
import com.eraherm.hermchat.data.local.GatewayRouteMode
import com.eraherm.hermchat.data.local.InputMode
import com.eraherm.hermchat.data.local.SpeakEngine
import com.eraherm.hermchat.data.local.WallpaperEntry
import com.eraherm.hermchat.data.local.WallpaperStore
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置树：根目录是文件夹，点进去才看详情。 */
private sealed interface PrefsFolder {
    data object Root : PrefsFolder
    data object Input : PrefsFolder
    data object Appearance : PrefsFolder
    data object Speak : PrefsFolder
    data object Shortcuts : PrefsFolder
    data object Gateway : PrefsFolder
}

@Composable
fun ChatPrefsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as HermChatApp
    val chatPrefs by app.chatPrefsStore.prefsFlow.collectAsStateWithLifecycle()
    var folder by remember { mutableStateOf<PrefsFolder>(PrefsFolder.Root) }

    BackHandler {
        if (folder == PrefsFolder.Root) onBack() else folder = PrefsFolder.Root
    }

    AtmosphereBackground(themeStyle = chatPrefs.themeStyle) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            BrandMark(compact = true)
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = folder,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "prefsFolder",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (current) {
                        PrefsFolder.Root -> PrefsRoot(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                            onOpenInput = { folder = PrefsFolder.Input },
                            onOpenAppearance = { folder = PrefsFolder.Appearance },
                            onOpenSpeak = { folder = PrefsFolder.Speak },
                            onOpenShortcuts = { folder = PrefsFolder.Shortcuts },
                            onOpenGateway = { folder = PrefsFolder.Gateway },
                            onOpenLibrary = onOpenLibrary,
                            onOpenAbout = onOpenAbout,
                        )
                        PrefsFolder.Input -> PrefsInputDetail(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                        )
                        PrefsFolder.Appearance -> PrefsAppearanceDetail(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                            wallpaperStore = app.wallpaperStore,
                        )
                        PrefsFolder.Speak -> PrefsSpeakDetail(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                            onOpenSystemTts = {
                                if (!app.replySpeaker.openSystemTtsSettings()) {
                                    // 无设置页可开时忽略
                                }
                            },
                        )
                        PrefsFolder.Shortcuts -> PrefsShortcutsDetail(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                        )
                        PrefsFolder.Gateway -> PrefsGatewayDetail(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = {
                        if (folder == PrefsFolder.Root) onBack() else folder = PrefsFolder.Root
                    },
                ) {
                    Text(if (folder == PrefsFolder.Root) "返回" else "上级")
                }
                if (folder == PrefsFolder.Root) {
                    TextButton(onClick = onOpenAbout) { Text("关于") }
                }
            }
        }
    }
}

@Composable
private fun PrefsRoot(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
    onOpenInput: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSpeak: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenGateway: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    PrefsFolderRow(
        title = "朗读",
        summary = buildString {
            append(if (prefs.autoSpeakReplies) "自动开" else "自动关")
            append(" · ")
            append(prefs.speakEngine.label)
            if (prefs.ttsEndpoint.isNotBlank()) append(" · TTS已填")
        },
        onClick = onOpenSpeak,
    )
    PrefsFolderRow(
        title = "端侧网关",
        summary = buildString {
            append(
                if (prefs.gatewayRouteMode == GatewayRouteMode.LOCAL) "本地模型" else "云端",
            )
            if (prefs.localFirstToolParse) append(" · 本地解析")
        },
        onClick = onOpenGateway,
    )
    PrefsLeafRow(
        title = "连接失败自动降级",
        trailing = {
            Switch(
                checked = prefs.connectionAutoDegrade,
                onCheckedChange = store::setConnectionAutoDegrade,
            )
        },
    )
    PrefsFolderRow(
        title = "资源库",
        summary = "Agent · 本地模型 · 搜索下载",
        onClick = onOpenLibrary,
    )
    PrefsFolderRow(
        title = "默认输入",
        summary = prefs.inputMode.label,
        onClick = onOpenInput,
    )
    PrefsFolderRow(
        title = "外观",
        summary = buildString {
            append(prefs.themeStyle.label)
            append(" · ")
            append(prefs.bubbleStyle.label)
            if (prefs.backgroundMode == ChatBackgroundMode.IMAGE) {
                append(" · 图片背景")
            }
        },
        onClick = onOpenAppearance,
    )
    PrefsFolderRow(
        title = "快捷指令",
        summary = "${prefs.shortcuts.size} 条",
        onClick = onOpenShortcuts,
    )
    PrefsFolderRow(
        title = "关于",
        summary = null,
        onClick = onOpenAbout,
    )
}

@Composable
private fun PrefsInputDetail(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
) {
    Text("默认输入", style = MaterialTheme.typography.titleMedium)
    InputMode.entries.forEach { mode ->
        PrefsOptionRow(
            title = mode.label,
            selected = prefs.inputMode == mode,
            onClick = { store.setInputMode(mode) },
        )
    }
}

@Composable
private fun PrefsSpeakDetail(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
    onOpenSystemTts: () -> Unit,
) {
    PrefsLeafRow(
        title = "自动朗读回复",
        trailing = {
            Switch(
                checked = prefs.autoSpeakReplies,
                onCheckedChange = { store.setAutoSpeakReplies(it) },
            )
        },
    )
    Text("朗读引擎", style = MaterialTheme.typography.titleMedium)
    SpeakEngine.entries.forEach { engine ->
        PrefsOptionRow(
            title = engine.label,
            selected = prefs.speakEngine == engine,
            onClick = { store.setSpeakEngine(engine) },
        )
    }
    Text(
        "自定义 TTS / 音色",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    OutlinedTextField(
        value = prefs.ttsEndpoint,
        onValueChange = store::setTtsEndpoint,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("TTS 地址") },
        placeholder = { Text("https://api.example.com/v1") },
        shape = RoundedCornerShape(14.dp),
    )
    OutlinedTextField(
        value = prefs.ttsApiKey,
        onValueChange = store::setTtsApiKey,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("TTS Key") },
        shape = RoundedCornerShape(14.dp),
    )
    OutlinedTextField(
        value = prefs.ttsModel,
        onValueChange = store::setTtsModel,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("模型") },
        placeholder = { Text("tts-1") },
        shape = RoundedCornerShape(14.dp),
    )
    OutlinedTextField(
        value = prefs.ttsVoice,
        onValueChange = store::setTtsVoice,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("音色") },
        placeholder = { Text("zh-CN-XiaoyiNeural") },
        shape = RoundedCornerShape(14.dp),
    )
    TextButton(
        onClick = onOpenSystemTts,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("打开系统语音包设置")
    }
}

@Composable
private fun PrefsGatewayDetail(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
) {
    var showLocalRouteWarn by remember { mutableStateOf(false) }
    var showLocalFirstWarn by remember { mutableStateOf(false) }
    val usingLocal = prefs.gatewayRouteMode == GatewayRouteMode.LOCAL

    Text("路由", style = MaterialTheme.typography.titleMedium)
    Text(
        text = if (usingLocal) "当前：本地模型" else "当前：云端",
        style = MaterialTheme.typography.bodyLarge,
    )
    if (usingLocal) {
        Button(
            onClick = { store.setGatewayRouteMode(GatewayRouteMode.API) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("改回云端")
        }
    } else {
        Button(
            onClick = { showLocalRouteWarn = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("使用本地模型")
        }
    }
    if (showLocalRouteWarn) {
        AlertDialog(
            onDismissRequest = { showLocalRouteWarn = false },
            title = { Text("改用本地模型？") },
            text = {
                Text("本地小模型能力有限，复杂任务可能不准；内存不足时可能失败。确认后网关对话将走本地。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.setGatewayRouteMode(GatewayRouteMode.LOCAL)
                        showLocalRouteWarn = false
                    },
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showLocalRouteWarn = false }) { Text("取消") }
            },
        )
    }

    PrefsLeafRow(
        title = "本地优先解析",
        trailing = {
            Switch(
                checked = prefs.localFirstToolParse,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showLocalFirstWarn = true
                    } else {
                        store.setLocalFirstToolParse(false)
                    }
                },
            )
        },
    )
    if (showLocalFirstWarn) {
        AlertDialog(
            onDismissRequest = { showLocalFirstWarn = false },
            title = { Text("开启本地优先解析？") },
            text = {
                Text("本地小模型更快但不稳，复杂指令可能失败并改走云端。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.setLocalFirstToolParse(true)
                        showLocalFirstWarn = false
                    },
                ) { Text("开启") }
            },
            dismissButton = {
                TextButton(onClick = { showLocalFirstWarn = false }) { Text("取消") }
            },
        )
    }

    PrefsLeafRow(
        title = "本地记忆",
        trailing = {
            Switch(
                checked = prefs.memoryEnabled,
                onCheckedChange = store::setMemoryEnabled,
            )
        },
    )
}

@Composable
private fun PrefsAppearanceDetail(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
    wallpaperStore: WallpaperStore,
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(WallpaperStore.PRESETS) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyId = "album"
            status = null
            val result = withContext(Dispatchers.IO) {
                wallpaperStore.importFromUri(uri)
            }
            busyId = null
            result.onSuccess { file ->
                store.setBackgroundImage(file.absolutePath, presetId = null)
                status = "已使用相册图片"
            }.onFailure {
                status = com.eraherm.hermchat.util.UserFacingError.of(it, "导入失败")
            }
        }
    }

    TextButton(
        onClick = {
            store.resetToSystemDefaultAppearance()
            status = "已恢复系统默认绿"
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("恢复系统默认绿")
    }

    Text("主题色", style = MaterialTheme.typography.titleMedium)
    ChatThemeStyle.entries.forEach { style ->
        PrefsOptionRow(
            title = style.label,
            selected = prefs.themeStyle == style,
            onClick = { store.setThemeStyle(style) },
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("气泡样式", style = MaterialTheme.typography.titleMedium)
    BubbleStyle.entries.forEach { style ->
        PrefsOptionRow(
            title = style.label,
            selected = prefs.bubbleStyle == style,
            onClick = { store.setBubbleStyle(style) },
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("聊天背景", style = MaterialTheme.typography.titleMedium)
    PrefsOptionRow(
        title = ChatBackgroundMode.THEME.label,
        selected = prefs.backgroundMode == ChatBackgroundMode.THEME,
        onClick = {
            store.setBackgroundThemeOnly()
            status = null
        },
    )
    TextButton(
        onClick = {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        enabled = busyId == null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (busyId == "album") "导入中…" else "从相册选择")
    }
    if (prefs.backgroundMode == ChatBackgroundMode.IMAGE) {
        TextButton(
            onClick = {
                store.setBackgroundThemeOnly()
                status = "已改回主题色背景"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("清除图片背景")
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text("搜索壁纸", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("关键词") },
        shape = RoundedCornerShape(14.dp),
    )
    TextButton(
        onClick = {
            scope.launch {
                busyId = "search"
                status = null
                val result = withContext(Dispatchers.IO) {
                    wallpaperStore.search(searchQuery)
                }
                busyId = null
                result.onSuccess {
                    results = it
                    if (it.isEmpty()) status = "没有结果"
                }.onFailure {
                    status = com.eraherm.hermchat.util.UserFacingError.of(it, "搜索失败")
                }
            }
        },
        enabled = busyId == null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (busyId == "search") "搜索中…" else "搜索")
    }

    results.forEach { entry ->
        WallpaperResultRow(
            entry = entry,
            selected = prefs.backgroundPresetId == entry.id &&
                prefs.backgroundMode == ChatBackgroundMode.IMAGE,
            busy = busyId == entry.id,
            downloaded = wallpaperStore.isDownloaded(entry.id),
            onUse = {
                scope.launch {
                    busyId = entry.id
                    status = null
                    val result = withContext(Dispatchers.IO) {
                        val local = wallpaperStore.localPath(entry.id)
                        if (local != null) {
                            Result.success(java.io.File(local))
                        } else {
                            wallpaperStore.download(entry)
                        }
                    }
                    busyId = null
                    result.onSuccess { file ->
                        store.setBackgroundImage(file.absolutePath, presetId = entry.id)
                        status = "已应用 ${entry.label}"
                    }.onFailure {
                        status = com.eraherm.hermchat.util.UserFacingError.of(it, "下载失败")
                    }
                }
            },
        )
    }

    status?.let {
        Text(it, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun WallpaperResultRow(
    entry: WallpaperEntry,
    selected: Boolean,
    busy: Boolean,
    downloaded: Boolean,
    onUse: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else Line,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (downloaded) "已缓存" else entry.source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                )
            }
            TextButton(onClick = onUse, enabled = !busy) {
                Text(
                    when {
                        busy -> "…"
                        selected -> "使用中"
                        downloaded -> "使用"
                        else -> "下载"
                    },
                )
            }
        }
    }
}

@Composable
private fun PrefsShortcutsDetail(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("快捷指令", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { store.resetShortcuts() }) {
            Text("恢复默认")
        }
    }
    prefs.shortcuts.forEachIndexed { index, shortcut ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Line),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shortcut.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                IconButton(
                    onClick = { store.moveShortcut(shortcut.id, -1) },
                    enabled = index > 0,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "前移",
                        tint = if (index > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            SoftGray
                        },
                    )
                }
                IconButton(
                    onClick = { store.moveShortcut(shortcut.id, 1) },
                    enabled = index < prefs.shortcuts.lastIndex,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "后移",
                        tint = if (index < prefs.shortcuts.lastIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            SoftGray
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrefsFolderRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SoftGray,
            )
        }
    }
}

@Composable
private fun PrefsLeafRow(
    title: String,
    trailing: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            trailing()
        }
    }
}

@Composable
private fun PrefsOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else Line,
        ),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
