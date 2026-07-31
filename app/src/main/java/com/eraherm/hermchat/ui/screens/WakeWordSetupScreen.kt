package com.eraherm.hermchat.ui.screens

import android.Manifest
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.WakeEngineKind
import com.eraherm.hermchat.data.local.WakeSettings
import com.eraherm.hermchat.service.AsrModelInstaller
import com.eraherm.hermchat.service.KwsModelInstaller
import com.eraherm.hermchat.service.WakeWordService
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.ui.theme.Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WakeWordSetupScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HermChatApp
    val settings by app.wakeSettingsStore.settings.collectAsStateWithLifecycle()
    val systemAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val modelReady = remember {
        mutableStateOf(
            KwsModelInstaller(context).isReady() && AsrModelInstaller(context).isReady(),
        )
    }
    var statusText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO] == true
        val notif = if (Build.VERSION.SDK_INT >= 33) {
            result[Manifest.permission.POST_NOTIFICATIONS] != false
        } else {
            true
        }
        if (mic && notif) {
            statusText = null
            WakeWordService.start(context)
            app.wakeSettingsStore.update { it.copy(enabled = true) }
        } else {
            statusText = "没有麦克风权限"
            app.wakeSettingsStore.update { it.copy(enabled = false) }
        }
    }

    fun requestStart() {
        val engine = settings.engine
        if (engine == WakeEngineKind.SYSTEM && !systemAvailable) {
            statusText = "本机暂无系统语音识别"
            app.wakeSettingsStore.update { it.copy(enabled = false) }
            return
        }
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(permissions)
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
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("引擎", style = MaterialTheme.typography.titleMedium)
                EngineOption(
                    label = "系统",
                    selected = settings.engine == WakeEngineKind.SYSTEM,
                    enabled = systemAvailable,
                    onClick = {
                        if (systemAvailable) {
                            app.wakeSettingsStore.update { it.copy(engine = WakeEngineKind.SYSTEM) }
                            statusText = null
                        } else {
                            statusText = "本机暂无系统语音识别"
                        }
                    },
                )
                EngineOption(
                    label = "离线",
                    selected = settings.engine == WakeEngineKind.OFFLINE,
                    enabled = true,
                    onClick = {
                        app.wakeSettingsStore.update { it.copy(engine = WakeEngineKind.OFFLINE) }
                        statusText = null
                    },
                )

                if (settings.engine == WakeEngineKind.OFFLINE && !modelReady.value) {
                    Button(
                        onClick = {
                            statusText = "下载中"
                            scope.launch {
                                val kws = withContext(Dispatchers.IO) {
                                    KwsModelInstaller(context).ensureInstalled()
                                }
                                if (kws.isFailure) {
                                    statusText = kws.exceptionOrNull()?.message ?: "下载失败"
                                    return@launch
                                }
                                val asr = withContext(Dispatchers.IO) {
                                    AsrModelInstaller(context).ensureInstalled()
                                }
                                asr.onSuccess {
                                    modelReady.value = true
                                    statusText = "模型已就绪"
                                }.onFailure {
                                    statusText = it.message ?: "下载失败"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("下载模型") }
                }

                Text("唤醒词", style = MaterialTheme.typography.titleMedium)
                WakeSettings.PRESETS.forEach { phrase ->
                    val selected = settings.phrase == phrase
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = {
                                    app.wakeSettingsStore.update { it.copy(phrase = phrase) }
                                },
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
                            text = phrase,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("识别后自动发送", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = settings.autoSend,
                        onCheckedChange = { checked ->
                            app.wakeSettingsStore.update { it.copy(autoSend = checked) }
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("后台持续监听", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                requestStart()
                            } else {
                                WakeWordService.stop(context)
                                app.wakeSettingsStore.update { it.copy(enabled = false) }
                            }
                        },
                    )
                }

                statusText?.let {
                    Text(
                        it,
                        color = if (it.contains("失败") || it.contains("没有") || it.contains("暂无")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("返回") }
                if (!settings.enabled) {
                    Button(
                        onClick = ::requestStart,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("开启监听") }
                } else {
                    OutlinedButton(
                        onClick = {
                            WakeWordService.stop(context)
                            app.wakeSettingsStore.update { it.copy(enabled = false) }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("停止监听") }
                }
            }
        }
    }
}

@Composable
private fun EngineOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(14.dp),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else Line,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            },
        )
    }
}
