package com.eraherm.hermchat.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.DeviceCapability
import com.eraherm.hermchat.data.local.LocalModelStore
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.data.network.HfModelSearch
import com.eraherm.hermchat.data.network.TransferProgress
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface LibraryFolder {
    data object Root : LibraryFolder
    data object Agents : LibraryFolder
    data object Models : LibraryFolder
    data object Search : LibraryFolder
}

@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onAddAgent: () -> Unit,
    onEditAgent: (AgentProfile) -> Unit,
) {
    val app = LocalContext.current.applicationContext as HermChatApp
    val agents by app.agentStore.agents.collectAsStateWithLifecycle()
    val currentId by app.agentStore.currentId.collectAsStateWithLifecycle()
    val customCatalog by app.localModelStore.customCatalog.collectAsStateWithLifecycle()
    var folder by remember { mutableStateOf<LibraryFolder>(LibraryFolder.Root) }
    var modelTick by remember { mutableStateOf(0) }
    val statuses = remember(customCatalog, modelTick) {
        app.localModelStore.listStatuses()
    }
    val scope = rememberCoroutineScope()
    var busyId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<TransferProgress?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var hfToken by remember { mutableStateOf(app.localModelStore.hfToken()) }
    var searchQuery by remember { mutableStateOf("qwen") }
    var searchResults by remember { mutableStateOf<List<LocalModelStore.ModelEntry>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun refreshModels() {
        modelTick += 1
    }

    fun pauseDownload() {
        val id = busyId
        if (id != null) app.localModelStore.pauseDownload(id)
        downloadJob?.cancel()
        downloadJob = null
        busyId = null
        progress = null
        refreshModels()
        statusMessage = LocalModelStore.PAUSED_MESSAGE
    }

    BackHandler {
        if (folder == LibraryFolder.Root) onBack() else folder = LibraryFolder.Root
    }

    fun startDownload(modelId: String, label: String) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            busyId = modelId
            progress = null
            statusMessage = null
            val result = try {
                withContext(Dispatchers.IO) {
                    app.localModelStore.ensureInstalled(
                        modelId = modelId,
                        hfToken = hfToken,
                        isActive = { isActive },
                    ) { p ->
                        scope.launch(Dispatchers.Main.immediate) {
                            progress = p
                        }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                Result.failure(IllegalStateException(LocalModelStore.PAUSED_MESSAGE))
            }
            busyId = null
            progress = null
            downloadJob = null
            refreshModels()
            statusMessage = result.fold(
                onSuccess = {
                    val warn = DeviceCapability.refuseReason(
                        app,
                        app.localModelStore.expectedBytes(modelId),
                    )
                    if (warn != null) "已下载 $label。$warn" else "已下载 $label"
                },
                onFailure = { err ->
                    if (err.message == LocalModelStore.PAUSED_MESSAGE) {
                        LocalModelStore.PAUSED_MESSAGE
                    } else {
                        com.eraherm.hermchat.util.UserFacingError.of(err, "下载失败")
                    }
                },
            )
        }
    }

    AtmosphereBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            BrandMark(compact = true)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (folder) {
                    LibraryFolder.Root -> "资源库"
                    LibraryFolder.Agents -> "Agent"
                    LibraryFolder.Models -> "本地模型"
                    LibraryFolder.Search -> "搜索开源模型"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = folder,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "libraryFolder",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (current) {
                        LibraryFolder.Root -> {
                            LibraryFolderRow(
                                title = "Agent",
                                summary = "${agents.size} 个",
                                onClick = { folder = LibraryFolder.Agents },
                            )
                            LibraryFolderRow(
                                title = "本地模型",
                                summary = "${statuses.count { it.installed }} 已装 · ${statuses.size} 目录",
                                onClick = { folder = LibraryFolder.Models },
                            )
                            LibraryFolderRow(
                                title = "搜索开源模型",
                                summary = "Hugging Face · litert-community",
                                onClick = { folder = LibraryFolder.Search },
                            )
                            Text(
                                text = "文件目录：${app.localModelStore.storageDir().absolutePath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftGray,
                            )
                        }

                        LibraryFolder.Agents -> {
                            TextButton(onClick = onAddAgent) { Text("添加 Agent") }
                            if (agents.isEmpty()) {
                                Text("还没有 Agent", color = SoftGray)
                            }
                            agents.forEach { agent ->
                                val selected = agent.id == currentId
                                LibraryCard {
                                    Text(agent.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = agent.kind.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SoftGray,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (!selected) {
                                            TextButton(onClick = {
                                                app.agentStore.setCurrentId(agent.id)
                                            }) { Text("选用") }
                                        } else {
                                            Text("当前", color = MaterialTheme.colorScheme.primary)
                                        }
                                        TextButton(onClick = { onEditAgent(agent) }) {
                                            Text("编辑")
                                        }
                                        TextButton(onClick = {
                                            app.agentStore.removeAgent(agent.id)
                                        }) { Text("删除") }
                                    }
                                }
                            }
                        }

                        LibraryFolder.Models -> {
                            Text(
                                text = "默认 Qwen / TinyLlama 可直接下载；Gemma 需上方令牌并在网页接受许可。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoftGray,
                            )
                            OutlinedTextField(
                                value = hfToken,
                                onValueChange = {
                                    hfToken = it
                                    app.localModelStore.setHfToken(it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Hugging Face 令牌（仅门控模型）") },
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            statuses.forEach { status ->
                                ModelStatusCard(
                                    status = status,
                                    busy = busyId == status.entry.id,
                                    progress = progress.takeIf { busyId == status.entry.id },
                                    onDownload = {
                                        startDownload(status.entry.id, status.entry.label)
                                    },
                                    onPause = { pauseDownload() },
                                    onAssign = {
                                        statusMessage = assignModelToCurrentAgent(
                                            app = app,
                                            agents = agents,
                                            currentId = currentId,
                                            modelId = status.entry.id,
                                            label = status.entry.label,
                                        )
                                    },
                                    onDelete = {
                                        app.localModelStore.uninstallAndForget(status.entry.id)
                                        refreshModels()
                                        statusMessage = "已移除 ${status.entry.label}"
                                    },
                                )
                            }
                        }

                        LibraryFolder.Search -> {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("关键词") },
                            )
                            OutlinedTextField(
                                value = hfToken,
                                onValueChange = {
                                    hfToken = it
                                    app.localModelStore.setHfToken(it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Hugging Face 令牌（搜索门控模型时）") },
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        searching = true
                                        statusMessage = null
                                        val result = withContext(Dispatchers.IO) {
                                            HfModelSearch.search(searchQuery, hfToken)
                                        }
                                        searching = false
                                        result.onSuccess {
                                            searchResults = it
                                            if (it.isEmpty()) statusMessage = "没有找到 .task 模型"
                                        }.onFailure {
                                            statusMessage = com.eraherm.hermchat.util.UserFacingError.of(it, "搜索失败")
                                        }
                                    }
                                },
                                enabled = !searching,
                            ) {
                                Text(if (searching) "搜索中…" else "搜索")
                            }
                            if (searching) {
                                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                            }
                            searchResults.forEach { entry ->
                                val installed = app.localModelStore.isReady(entry.id)
                                LibraryCard {
                                    Text(entry.label, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = buildString {
                                            append(entry.source)
                                            if (entry.approxBytes > 0) {
                                                append(" · 约 ")
                                                append(TransferProgress.formatBytes(entry.approxBytes))
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SoftGray,
                                    )
                                    if (busyId == entry.id && progress != null) {
                                        LinearProgressIndicator(
                                            progress = { progress!!.fraction.coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text(
                                            progress!!.statusLine(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SoftGray,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (installed) {
                                            Text("已安装", color = MaterialTheme.colorScheme.primary)
                                        } else if (busyId == entry.id) {
                                            TextButton(onClick = { pauseDownload() }) {
                                                Text("暂停")
                                            }
                                        } else {
                                            TextButton(
                                                enabled = busyId == null,
                                                onClick = {
                                                    app.localModelStore.register(entry)
                                                    startDownload(entry.id, entry.label)
                                                },
                                            ) {
                                                Text(
                                                    if (app.localModelStore.hasPartial(entry.id)) {
                                                        "继续下载"
                                                    } else {
                                                        "加入并下载"
                                                    },
                                                )
                                            }
                                        }
                                        TextButton(onClick = {
                                            app.localModelStore.register(entry)
                                            refreshModels()
                                            statusMessage = "已加入目录，可在「本地模型」下载"
                                        }) { Text("仅加入目录") }
                                    }
                                }
                            }
                        }
                    }

                    statusMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = {
                    if (folder == LibraryFolder.Root) onBack() else folder = LibraryFolder.Root
                },
            ) {
                Text(if (folder == LibraryFolder.Root) "返回" else "上级")
            }
        }
    }
}

private fun assignModelToCurrentAgent(
    app: HermChatApp,
    agents: List<AgentProfile>,
    currentId: String?,
    modelId: String,
    label: String,
): String {
    val current = agents.find { it.id == currentId } ?: agents.firstOrNull()
        ?: return "请先添加本地或端侧网关 Agent"
    return when (current.kind) {
        AgentKind.LOCAL -> {
            app.agentStore.saveAgent(current.copy(model = modelId), setCurrent = true)
            "已选用到「${current.name}」"
        }
        AgentKind.GATEWAY -> {
            app.agentStore.saveAgent(
                current.copy(localModelId = modelId),
                setCurrent = true,
            )
            "已设为「${current.name}」本地兜底（$label）"
        }
        else -> "当前是 ${current.kind.label}，请先切换到本地或端侧网关"
    }
}

@Composable
private fun ModelStatusCard(
    status: LocalModelStore.ModelStatus,
    busy: Boolean,
    progress: TransferProgress?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onAssign: () -> Unit,
    onDelete: () -> Unit,
) {
    LibraryCard {
        Text(status.entry.label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = buildString {
                when {
                    status.installed -> {
                        append("已安装 · ")
                        append(TransferProgress.formatBytes(status.bytesOnDisk))
                    }
                    status.partial -> {
                        append("未下完 · ")
                        append(TransferProgress.formatBytes(status.bytesOnDisk))
                        if (status.entry.approxBytes > 0) {
                            append(" / ")
                            append(TransferProgress.formatBytes(status.entry.approxBytes))
                        }
                    }
                    else -> {
                        append("未下载")
                        if (status.entry.approxBytes > 0) {
                            append(" · 约 ")
                            append(TransferProgress.formatBytes(status.entry.approxBytes))
                        }
                    }
                }
                append(" · ")
                append(status.entry.fileName)
            },
            style = MaterialTheme.typography.bodySmall,
            color = SoftGray,
        )
        if (busy && progress != null) {
            LinearProgressIndicator(
                progress = { progress.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                progress.statusLine(),
                style = MaterialTheme.typography.bodySmall,
                color = SoftGray,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!status.installed) {
                if (busy) {
                    TextButton(onClick = onPause) { Text("暂停") }
                } else {
                    TextButton(onClick = onDownload) {
                        Text(if (status.partial) "继续" else "下载")
                    }
                }
            } else {
                TextButton(onClick = onAssign, enabled = !busy) { Text("选用到当前") }
                TextButton(onClick = onDelete, enabled = !busy) { Text("删除文件") }
            }
        }
    }
}

@Composable
private fun LibraryFolderRow(
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
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
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
private fun LibraryCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Line),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}
