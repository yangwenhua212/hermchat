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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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

/** 参考图网格：一行三张，点开看大图。 */
private const val REF_COLUMNS = 3

/** 缩略图高度 / 大图最大高度。 */
private val REF_TILE_HEIGHT = 88.dp
private val REF_PREVIEW_MAX_HEIGHT = 420.dp

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
    var chatDraft by remember { mutableStateOf("") }
    var project by remember { mutableStateOf(config.project) }
    var showService by remember { mutableStateOf(false) }
    var showTermuxDialog by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    var refKind by remember { mutableStateOf("character") }
    var refKey by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<HxmvRef?>(null) }
    var batchUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // 选图走系统相册（GetContent 系列 image 类型），与聊天附件同一个走法；支持一次多选
    val pickRef = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        when {
            uris.isEmpty() -> Unit
            uris.size == 1 -> viewModel.uploadRefs(listOf(uris.first() to refKey), refKind)
            else -> batchUris = uris
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ui.statusLine,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                // 没连上时给一次手动机会：不用进「服务」对话框也能重探
                if (!ui.connected && !ui.needsToken) {
                    TextButton(onClick = { viewModel.checkConnection() }) { Text("重试") }
                }
            }
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
                // ── 对话：像聊天一样说需求（老大要求「hxmv 也需要可以聊天」）──
                Text("对话", style = MaterialTheme.typography.titleMedium)
                if (ui.chat.isEmpty()) {
                    Text(
                        "说一句想做什么（如「一只柯基在雪地里打滚，8 秒」）：它会回你话并给出可执行的目标，点「开工」才真的开始跑（不点不花钱、不落档）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ui.chat.forEach { line ->
                    val mine = line.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (mine) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(line.text, style = MaterialTheme.typography.bodyMedium)
                            line.goal?.let { g ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { goal = g },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                ) { Text("开工", maxLines = 1) }
                            }
                        }
                    }
                }
                if (ui.chatBusy) {
                    Text("它正在想…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = chatDraft,
                        onValueChange = { chatDraft = it },
                        label = { Text("跟 HxMV 说") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            viewModel.sendChat(chatDraft)
                            chatDraft = ""
                        },
                        enabled = chatDraft.isNotBlank() && !ui.chatBusy,
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("发送") }
                }

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

                // 不满意就删：删掉这次的产物，并撤销它在项目档案里的登记（别让它继续当"设定"）
                ui.currentRunId?.takeIf { !ui.running }?.let { runId ->
                    OutlinedButton(
                        onClick = { viewModel.discard(runId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("不满意，删掉这条") }
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
                ) { Text(if (ui.refBusy) "处理中" else "选图上传（可多选）") }
                ui.refs.chunked(REF_COLUMNS).forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        line.forEach { ref ->
                            RefTile(
                                ref = ref,
                                load = viewModel::refImage,
                                onClick = { preview = ref },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(REF_COLUMNS - line.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
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

    preview?.let { ref ->
        RefPreviewDialog(
            ref = ref,
            load = viewModel::refImage,
            onDelete = { viewModel.deleteRef(ref) },
            onDismiss = { preview = null },
        )
    }

    if (batchUris.isNotEmpty()) {
        BatchRefDialog(
            count = batchUris.size,
            base = refKey,
            onDismiss = { batchUris = emptyList() },
            onConfirm = { names ->
                viewModel.uploadRefs(
                    batchUris.mapIndexed { index, uri -> uri to names.getOrElse(index) { "$refKey${index + 1}" } },
                    refKind,
                )
                batchUris = emptyList()
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
                    // 不预填任何地址：HxSync 是开源 App，预填作者的实例等于让所有下载的人都用他的服务器
                    placeholder = { Text("你自己的 HxMV 实例：http://127.0.0.1:8668 或你的域名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("令牌") },
                    placeholder = { Text("实例的 HXMV_WEB_TOKEN（没设可留空）") },
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

/** 一张参考图：缩略图 + 名字；点开是大图（删除放那儿，省得网格里误触）。 */
@Composable
private fun RefTile(
    ref: HxmvRef,
    load: suspend (HxmvRef) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var thumb by remember(ref.key, ref.url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ref.key, ref.url) { thumb = load(ref) }
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(REF_TILE_HEIGHT)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(REF_TILE_HEIGHT)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Text(
            "${ref.kindLabel} · ${ref.name}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 参考图大图：看清用的是哪张；删除放这里（网格里点一下只预览，不会误删）。 */
@Composable
private fun RefPreviewDialog(
    ref: HxmvRef,
    load: suspend (HxmvRef) -> Bitmap?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var image by remember(ref.key, ref.url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ref.key, ref.url) { image = load(ref) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${ref.kindLabel} · ${ref.name}") },
        text = {
            val bitmap = image
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = REF_PREVIEW_MAX_HEIGHT)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("读取中…", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(); onDismiss() }) { Text("删除") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 多图上传：一次选了好几张口，逐张起名（默认「名字1/名字2…」），确认后批量上传。 */
@Composable
private fun BatchRefDialog(
    count: Int,
    base: String,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val names = remember(count, base) {
        mutableStateListOf<String>().apply { repeat(count) { add("$base${it + 1}") } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("给这 $count 张起名") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                names.forEachIndexed { index, name ->
                    OutlinedTextField(
                        value = name,
                        onValueChange = { names[index] = it },
                        label = { Text("第 ${index + 1} 张") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(names.toList()) }) { Text("上传 $count 张") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
