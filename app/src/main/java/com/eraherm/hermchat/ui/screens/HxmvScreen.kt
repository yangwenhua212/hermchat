package com.eraherm.hermchat.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.HxmvConfig
import com.eraherm.hermchat.data.local.HxmvProvider
import com.eraherm.hermchat.data.network.HxmvArtifact
import com.eraherm.hermchat.data.network.HxmvConfigState
import com.eraherm.hermchat.data.network.HxmvRef
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.viewmodel.HxmvViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TERMUX_URL = "https://github.com/termux/termux-app/releases"

/** 参考图的两类（值 = 服务端 kind）。 */
private val REF_KINDS = listOf(
    "character" to "角色",
    "scene" to "场景",
)

/** 视频档位（值 = 服务端 config 里的 video_model）。 */
private val VIDEO_MODELS = listOf(
    "cogvideox-flash" to "免费档",
    "cogvideox-2" to "0.5 元/次",
    "cogvideox-3" to "1 元/次",
)

/** 视觉评审档位（vlm_model）。 */
private val VLM_MODELS = listOf(
    "glm-4v-flash" to "免费档",
    "glm-4.6v-flashx" to "0.15 元/百万",
    "glm-4.6v" to "1 元/百万",
)

/**
 * HxMV 内容生产页：远端实例与手机本机（Termux）同一套流程，只有地址不同。
 * 文案遵守 docs/UI.md：只留可操作主文案，状态一句话，不堆说明灰字。
 */
@Composable
fun HxmvScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HermChatApp
    val viewModel: HxmvViewModel = viewModel(factory = HxmvViewModel.factory(app))
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var goal by remember { mutableStateOf("") }
    var project by remember { mutableStateOf(config.project) }
    var showService by remember { mutableStateOf(false) }
    var showTermuxDialog by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    var refKind by remember { mutableStateOf("character") }
    var refKey by remember { mutableStateOf("") }

    // 选图走系统相册（GetContent image 类型），与聊天附件同一个走法
    val pickRef = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadRef(uri, refKind, refKey)
    }

    BackHandler(onBack = onBack)

    // 用户去装完 Termux 再回来时刷新状态（③ 检测本机之前就能看到「Termux 已装好」）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshTermux()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ui.message?.let { text ->
        LaunchedEffect(text) {
            delay(2500)
            viewModel.clearMessage()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(compact = true)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showConfig = true; viewModel.loadRemoteConfig() }) { Text("配置") }
                TextButton(onClick = { showService = true }) { Text("服务") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(ui.statusLine, style = MaterialTheme.typography.bodyMedium)
            if (ui.needsToken) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showService = true },
                    shape = RoundedCornerShape(12.dp),
                ) { Text("填令牌") }
            }
            ui.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (ui.localInstanceFound && config.baseUrl != HxmvConfig.LOCAL_BASE) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.switchToLocalInstance() },
                    shape = RoundedCornerShape(12.dp),
                ) { Text("切换到手机本机") }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("想做什么内容") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HxmvProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = config.provider == provider,
                            onClick = { viewModel.updateConfig { it.copy(provider = provider) } },
                            label = { Text(provider.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = project,
                    onValueChange = {
                        project = it
                        viewModel.updateConfig { old -> old.copy(project = it) }
                    },
                    label = { Text("项目") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.start(goal) },
                    enabled = goal.isNotBlank() && !ui.running,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (ui.running) "生产中" else "开始生产") }

                if (ui.tasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("生产进度", style = MaterialTheme.typography.titleMedium)
                    ui.tasks.forEach { line ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(line.action, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(line.state, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.weight(1f))
                            if (line.detail.isNotBlank()) {
                                Text(line.detail, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (ui.artifacts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("成品", style = MaterialTheme.typography.titleMedium)
                    ui.artifacts.forEach { artifact ->
                        ArtifactRow(
                            artifact = artifact,
                            onPlay = {
                                scope.launch {
                                    val file = viewModel.downloadForPlay(artifact) ?: return@launch
                                    openLocal(context, file)
                                }
                            },
                            onSave = { viewModel.saveToGallery(artifact) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("参考图", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (config.project.isBlank()) "先填上面的项目名" else "当前项目：${config.project}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    REF_KINDS.forEach { (value, label) ->
                        FilterChip(
                            selected = refKind == value,
                            onClick = { refKind = value },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = refKey,
                    onValueChange = { refKey = it },
                    label = { Text("名字（镜头里用的角色名/场景名）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { pickRef.launch("image/*") },
                    enabled = config.project.isNotBlank() && refKey.isNotBlank() && !ui.refBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (ui.refBusy) "处理中" else "选图上传") }
                ui.refs.forEach { ref ->
                    RefRow(
                        ref = ref,
                        load = viewModel::refImage,
                        onDelete = { viewModel.deleteRef(ref) },
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("手机部署（可选）", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (ui.localInstanceFound) "当前：手机本机跑" else "当前：服务器出片（不装也能用）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = {
                        if (ui.termuxInstalled) {
                            viewModel.showMessage("Termux 已装好，走下一步")
                        } else {
                            showTermuxDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (ui.termuxInstalled) "① Termux 已装好" else "① 装 Termux（手机运行环境）") }
                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, viewModel.termuxCommand())
                        viewModel.showMessage("已复制，去 Termux 粘贴回车")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("② 复制一键安装命令") }
                OutlinedButton(
                    onClick = { viewModel.probeLocalNow() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (ui.localInstanceFound) "③ 已连上本机 HxMV" else "③ 检测本机 HxMV") }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
    }

    if (showTermuxDialog) {
        AlertDialog(
            onDismissRequest = { showTermuxDialog = false },
            title = { Text("装 Termux") },
            text = {
                Text(
                    "Termux 是手机上的终端 App，开源项目，不是 HxSync。\n" +
                        "HxMV 要靠它跑在你手机里（装完这一路就都在手机上了）。\n" +
                        "点下面的按钮会打开它的官方下载页。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTermuxDialog = false
                        openInBrowser(context, TERMUX_URL)
                    },
                ) { Text("去下载 Termux") }
            },
            dismissButton = {
                TextButton(onClick = { showTermuxDialog = false }) { Text("取消") }
            },
        )
    }

    if (showService) {
        ServiceDialog(
            initial = config,
            onDismiss = { showService = false },
            onSave = { base, token ->
                viewModel.updateConfig { it.copy(baseUrl = base, token = token) }
                viewModel.checkConnection()
                showService = false
            },
        )
    }

    if (showConfig) {
        ConfigDialog(
            state = ui.remoteConfig,
            onDismiss = { showConfig = false },
            onSave = { key, video, vlm ->
                viewModel.saveRemoteConfig(key, video, vlm)
                showConfig = false
            },
        )
    }
}

@Composable
private fun ArtifactRow(
    artifact: HxmvArtifact,
    onPlay: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${artifact.label} · ${artifact.name}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text("${artifact.size / 1024} KB", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.width(6.dp))
        TextButton(onClick = onPlay) { Text("播放") }
        TextButton(onClick = onSave) { Text("保存") }
    }
}

@Composable
private fun ServiceDialog(
    initial: HxmvConfig,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var base by remember { mutableStateOf(initial.baseUrl) }
    var token by remember { mutableStateOf(initial.token) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("令牌") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(base, token) }) { Text("保存并检测") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 一行参考图：缩略图 + 「类型 · 名字」 + 删除。 */
@Composable
private fun RefRow(
    ref: HxmvRef,
    load: suspend (HxmvRef) -> Bitmap?,
    onDelete: () -> Unit,
) {
    var thumb by remember(ref.key, ref.url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ref.key, ref.url) { thumb = load(ref) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "${ref.kindLabel} · ${ref.name}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDelete) { Text("删除") }
    }
}

/** 接口配置：Key 状态 + 视频/视觉档位（保存即生效，不用重启实例）。 */
@Composable
private fun ConfigDialog(
    state: HxmvConfigState?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var video by remember(state?.videoModel) { mutableStateOf(state?.videoModel.orEmpty()) }
    var vlm by remember(state?.vlmModel) { mutableStateOf(state?.vlmModel.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("接口配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when {
                        state == null -> "正在读取…"
                        state.keyConfigured -> "智谱 Key：已配置 ${state.keyMasked}"
                        else -> "智谱 Key：未配置"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("智谱 Key（留空不改）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("视频模型", style = MaterialTheme.typography.bodyMedium)
                ChipRow(options = VIDEO_MODELS, selected = video, onSelect = { video = it })
                Text("视觉评审", style = MaterialTheme.typography.bodyMedium)
                ChipRow(options = VLM_MODELS, selected = vlm, onSelect = { vlm = it })
                Text(
                    if (state?.visionReady == true) "视觉评审：已就绪" else "视觉评审：未就绪",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key, video, vlm) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

private fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("hxmv", text))
}

/** 用系统播放器打开缓存里的成品（走 FileProvider，无需存储权限）。 */
private fun openLocal(context: Context, file: java.io.File) {
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // 没有可播放的 App 时静默返回（不弹灰字教程）
    }
}
