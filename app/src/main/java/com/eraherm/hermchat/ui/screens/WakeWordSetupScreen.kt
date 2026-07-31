package com.eraherm.hermchat.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.WakeSettings
import com.eraherm.hermchat.service.WakeWordService
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray

@Composable
fun WakeWordSetupScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HermChatApp
    val settings by app.wakeSettingsStore.settings.collectAsStateWithLifecycle()
    var permissionHint by remember { mutableStateOf<String?>(null) }

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
            permissionHint = null
            WakeWordService.start(context)
            app.wakeSettingsStore.update { it.copy(enabled = true) }
        } else {
            permissionHint = "没有麦克风权限就无法唤醒"
            app.wakeSettingsStore.update { it.copy(enabled = false) }
        }
    }

    fun requestStart() {
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
        BrandMark(
            subtitle = "选一个预设名字，后台听你喊它",
            compact = true,
        )
        Text(
            text = "当前用系统语音识别；接口已预留，后续可换 sherpa-onnx / Vosk。",
            style = MaterialTheme.typography.bodyMedium,
            color = SoftGray,
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("识别后自动发送", style = MaterialTheme.typography.bodyLarge)
                    Text("关掉则只填进输入框", style = MaterialTheme.typography.bodyMedium, color = SoftGray)
                }
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("后台持续监听", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (settings.enabled) "已开启 · 通知栏可停止" else "关闭时仍可用麦克风按钮",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                    )
                }
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

            permissionHint?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Text(
                text = "国产 ROM 若杀后台，请把 HxSync 加入省电白名单。",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
            )
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
