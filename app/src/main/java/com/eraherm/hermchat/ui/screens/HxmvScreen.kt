package com.eraherm.hermchat.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.HxmvConfig
import com.eraherm.hermchat.data.local.HxmvProvider
import com.eraherm.hermchat.data.network.HxmvArtifact
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.viewmodel.HxmvViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TERMUX_URL = "https://github.com/termux/termux-app/releases"

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

    BackHandler(onBack = onBack)

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
                TextButton(onClick = { showService = true }) { Text("服务") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(ui.statusLine, style = MaterialTheme.typography.bodyMedium)
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
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                TextButton(
                    onClick = {
                        if (!ui.termuxInstalled) {
                            openInBrowser(context, TERMUX_URL)
                            viewModel.showMessage("装好 Termux 再回来")
                        } else {
                            copyToClipboard(context, viewModel.termuxCommand())
                            viewModel.showMessage("安装命令已复制")
                        }
                    },
                ) { Text(if (ui.termuxInstalled) "复制安装命令" else "装到手机") }
            }
        }
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
