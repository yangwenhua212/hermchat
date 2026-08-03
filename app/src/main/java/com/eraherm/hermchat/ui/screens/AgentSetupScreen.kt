package com.eraherm.hermchat.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.LocalModelStore
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.data.network.EndpointProbe
import com.eraherm.hermchat.data.network.ProbeHit
import com.eraherm.hermchat.data.network.TransferProgress
import com.eraherm.hermchat.ui.PortraitCaptureActivity
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray
import com.eraherm.hermchat.viewmodel.SetupViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun AgentSetupScreen(
    sessionKey: String = "setup",
    editing: AgentProfile? = null,
    onFinished: (AgentProfile) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HermChatApp
    val viewModel: SetupViewModel = viewModel(
        key = sessionKey,
        factory = SetupViewModel.factory(
            agentStore = app.agentStore,
            endpointProbe = EndpointProbe(app),
            localModelStore = app.localModelStore,
            appContext = app,
            initial = editing,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val customCatalog by app.localModelStore.customCatalog.collectAsStateWithLifecycle()
    val modelCatalog = remember(customCatalog) {
        app.localModelStore.allCatalog().values.toList()
            .sortedBy { it.label }
    }
    BackHandler(enabled = uiState.step > 1 || onCancel != null) {
        if (uiState.step > 1) {
            viewModel.previousStep()
        } else {
            onCancel?.invoke()
        }
    }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteDraft by remember { mutableStateOf("") }
    var cameraHint by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            viewModel.applyImport(contents)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            cameraHint = null
            scanLauncher.launch(
                ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("将二维码放入框内")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                    setCaptureActivity(PortraitCaptureActivity::class.java)
                },
            )
        } else {
            cameraHint = "没有相机权限时，可改用「粘贴配置」"
            showPasteDialog = true
        }
    }

    fun startScan() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        if (text.isNotBlank()) {
            pasteDraft = text
        }
        showPasteDialog = true
    }

    LaunchedEffect(uiState.completedProfile) {
        uiState.completedProfile?.let { profile ->
            viewModel.consumeCompleted()
            onFinished(profile)
        }
    }

    AtmosphereBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            BrandMark(compact = true)
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
                            onScan = ::startScan,
                            onPaste = ::pasteFromClipboard,
                        )
                        2 -> StepEndpoint(
                            kind = uiState.kind,
                            endpoint = uiState.endpoint,
                            apiKey = uiState.apiKey,
                            model = uiState.model,
                            localModelId = uiState.localModelId,
                            modelCatalog = modelCatalog,
                            modelStore = app.localModelStore,
                            testing = uiState.testing,
                            testPassed = uiState.testPassed,
                            testMessage = uiState.testMessage,
                            probing = uiState.probing,
                            probeHits = uiState.probeHits,
                            probeMessage = uiState.probeMessage,
                            downloadingModel = uiState.downloadingModel,
                            downloadProgress = uiState.downloadProgress,
                            downloadDetail = uiState.downloadDetail,
                            modelReady = uiState.modelReady,
                            onEndpointChange = viewModel::updateEndpoint,
                            onApiKeyChange = viewModel::updateApiKey,
                            onModelChange = viewModel::updateModel,
                            onLocalModelChange = viewModel::updateLocalModelId,
                            onTest = viewModel::testConnection,
                            onDownloadModel = viewModel::downloadLocalModel,
                            onPauseDownload = viewModel::pauseLocalModelDownload,
                            onUsePreset = { kind -> viewModel.selectKind(kind) },
                            onSkipTest = viewModel::skipTestAndContinue,
                            onProbe = viewModel::probeEndpoints,
                            onUseHit = viewModel::useProbeHit,
                            onScan = ::startScan,
                            onPaste = ::pasteFromClipboard,
                        )
                        else -> StepName(
                            name = uiState.name,
                            onNameChange = viewModel::updateName,
                        )
                    }
                    cameraHint?.let { hint ->
                        Text(
                            text = hint,
                            color = SoftGray,
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
                    enabled = !uiState.testing && !uiState.saving && !uiState.probing,
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

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("粘贴配置") },
            text = {
                OutlinedTextField(
                    value = pasteDraft,
                    onValueChange = { pasteDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("配置内容") },
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasteDialog = false
                        viewModel.applyImport(pasteDraft)
                    },
                    enabled = pasteDraft.isNotBlank(),
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text("取消") }
            },
        )
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
private fun ImportActions(
    onScan: () -> Unit,
    onPaste: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onScan,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        ) { Text("扫码导入") }
        OutlinedButton(
            onClick = onPaste,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        ) { Text("粘贴配置") }
    }
}

@Composable
private fun StepSelectKind(
    selected: AgentKind,
    onSelect: (AgentKind) -> Unit,
    onScan: () -> Unit,
    onPaste: () -> Unit,
) {
    Text(
        text = "选择类型",
        style = MaterialTheme.typography.bodyLarge,
    )
    ImportActions(onScan = onScan, onPaste = onPaste)
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
            Text(
                text = kind.label,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun StepEndpoint(
    kind: AgentKind,
    endpoint: String,
    apiKey: String,
    model: String,
    localModelId: String,
    modelCatalog: List<LocalModelStore.ModelEntry>,
    modelStore: LocalModelStore,
    testing: Boolean,
    testPassed: Boolean,
    testMessage: String?,
    probing: Boolean,
    probeHits: List<ProbeHit>,
    probeMessage: String?,
    downloadingModel: Boolean,
    downloadProgress: Float,
    downloadDetail: String?,
    modelReady: Boolean,
    onEndpointChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onLocalModelChange: (String) -> Unit,
    onTest: () -> Unit,
    onDownloadModel: () -> Unit,
    onPauseDownload: () -> Unit,
    onUsePreset: (AgentKind) -> Unit,
    onSkipTest: () -> Unit,
    onProbe: () -> Unit,
    onUseHit: (ProbeHit) -> Unit,
    onScan: () -> Unit,
    onPaste: () -> Unit,
) {
    if (kind == AgentKind.LOCAL) {
        Text("本地模型", style = MaterialTheme.typography.bodyLarge)
        LocalModelPicker(
            catalog = modelCatalog,
            selectedId = model.ifBlank { LocalModelStore.DEFAULT_MODEL_ID },
            modelStore = modelStore,
            onSelect = onModelChange,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("下载令牌") },
            shape = RoundedCornerShape(16.dp),
        )
        val localId = model.ifBlank { LocalModelStore.DEFAULT_MODEL_ID }
        val hasPartial = !modelReady && modelStore.hasPartial(localId)
        if (downloadingModel) {
            Button(
                onClick = onPauseDownload,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("暂停 ${(downloadProgress * 100).toInt()}%")
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { downloadProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            downloadDetail?.let { detail ->
                Text(
                    text = detail,
                    color = SoftGray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Button(
                onClick = onDownloadModel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    when {
                        modelReady -> "重新下载"
                        hasPartial -> "继续下载"
                        else -> "下载模型"
                    },
                )
            }
            downloadDetail?.let { detail ->
                Text(
                    text = detail,
                    color = SoftGray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Button(
            onClick = onTest,
            enabled = !testing && !downloadingModel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(if (testing) "测试中…" else "测试")
        }
        when {
            testPassed && modelReady -> Text(
                text = "模型已就绪",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            testPassed -> Text(
                text = "编排已就绪",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            !testMessage.isNullOrBlank() -> Text(
                text = testMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!testPassed) {
            TextButton(onClick = onSkipTest) { Text("跳过测连") }
        }
        return
    }

    if (kind == AgentKind.GATEWAY) {
        Text("端侧网关", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "简单题走本地，难题走 API；闹钟/日历在本机",
            color = SoftGray,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API Base URL") },
            placeholder = { Text("https://api.deepseek.com") },
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API Key（兼作下载令牌）") },
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API 模型") },
            placeholder = { Text("deepseek-chat") },
            shape = RoundedCornerShape(16.dp),
        )
        Text("本地兜底模型", style = MaterialTheme.typography.bodyLarge)
        LocalModelPicker(
            catalog = modelCatalog,
            selectedId = localModelId.ifBlank { LocalModelStore.DEFAULT_MODEL_ID },
            modelStore = modelStore,
            onSelect = onLocalModelChange,
        )
        val gatewayLocalId = localModelId.ifBlank { LocalModelStore.DEFAULT_MODEL_ID }
        val gatewayPartial = !modelReady && modelStore.hasPartial(gatewayLocalId)
        if (downloadingModel) {
            Button(
                onClick = onPauseDownload,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("暂停 ${(downloadProgress * 100).toInt()}%")
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { downloadProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            downloadDetail?.let { detail ->
                Text(text = detail, color = SoftGray, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(
                onClick = onDownloadModel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    when {
                        modelReady -> "重新下载本地模型"
                        gatewayPartial -> "继续下载本地模型"
                        else -> "下载本地兜底模型"
                    },
                )
            }
            downloadDetail?.let { detail ->
                Text(text = detail, color = SoftGray, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = onTest,
            enabled = endpoint.isNotBlank() && !testing && !downloadingModel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(if (testing) "测试中…" else "测试 API")
        }
        when {
            testPassed -> Text(
                text = testMessage ?: "API 可达",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            !testMessage.isNullOrBlank() -> Text(
                text = testMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!testPassed) {
            TextButton(onClick = onSkipTest) { Text("跳过测连") }
        }
        return
    }

    if (kind == AgentKind.HERMES) {
        Text(
            text = "连接 Hermes",
            style = MaterialTheme.typography.bodyLarge,
        )
        ImportActions(onScan = onScan, onPaste = onPaste)
        OutlinedTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("主机") },
            placeholder = { Text("IP 或域名") },
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API Key") },
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("模型名") },
            shape = RoundedCornerShape(16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onProbe,
                enabled = !probing && !testing,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (probing) "探测中…" else "自动探测")
            }
            Button(
                onClick = onTest,
                enabled = endpoint.isNotBlank() && !testing && !probing,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (testing) "测试中…" else "测试")
            }
        }
        if (probeHits.isEmpty() && !probeMessage.isNullOrBlank() && !probing) {
            Text(
                text = "未发现端点",
                color = SoftGray,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        probeHits.forEach { hit ->
            val selected = hit.endpoint == endpoint
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { onUseHit(hit) },
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
                    text = hit.endpoint,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        when {
            testPassed -> Text(
                text = "测连成功",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            !testMessage.isNullOrBlank() -> Text(
                text = testMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!testPassed) {
            TextButton(onClick = onSkipTest) {
                Text("跳过测连")
            }
        }
        return
    }

    val showHttpFields = kind == AgentKind.HTTP_COMPAT ||
        endpoint.startsWith("http://", ignoreCase = true) ||
        endpoint.startsWith("https://", ignoreCase = true)

    Text(
        text = "填地址",
        style = MaterialTheme.typography.bodyLarge,
    )
    ImportActions(onScan = onScan, onPaste = onPaste)
    OutlinedTextField(
        value = endpoint,
        onValueChange = onEndpointChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Agent 地址") },
        shape = RoundedCornerShape(16.dp),
    )
    if (showHttpFields) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("API Key") },
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("模型名") },
            placeholder = { Text("如 deepseek-chat") },
            shape = RoundedCornerShape(16.dp),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onProbe,
            enabled = !probing && !testing,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (probing) "探测中…" else "自动探测")
        }
        Button(
            onClick = onTest,
            enabled = endpoint.isNotBlank() && !testing && !probing,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (testing) "测试中…" else "测试")
        }
    }
    OutlinedButton(
        onClick = { onUsePreset(kind) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("恢复预设地址")
    }
    if (probeHits.isEmpty() && !probeMessage.isNullOrBlank() && !probing) {
        Text(
            text = "未发现端点",
            color = SoftGray,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    probeHits.forEach { hit ->
        val selected = hit.endpoint == endpoint
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = { onUseHit(hit) },
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
                text = hit.endpoint,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    when {
        testPassed -> Text(
            text = "测连成功",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        !testMessage.isNullOrBlank() -> Text(
            text = testMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (!testPassed) {
        TextButton(onClick = onSkipTest) {
            Text("跳过测连")
        }
    }
}

@Composable
private fun StepName(
    name: String,
    onNameChange: (String) -> Unit,
) {
    Text(
        text = "起名字",
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
}

@Composable
private fun LocalModelPicker(
    catalog: List<LocalModelStore.ModelEntry>,
    selectedId: String,
    modelStore: LocalModelStore,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        catalog.forEach { entry ->
            val selected = entry.id == selectedId
            val installed = modelStore.isReady(entry.id)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(entry.id) },
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
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(entry.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = buildString {
                            append(if (installed) "已安装" else "未下载")
                            if (entry.approxBytes > 0L) {
                                append(" · 约 ")
                                append(TransferProgress.formatBytes(entry.approxBytes))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftGray,
                    )
                }
            }
        }
    }
}
