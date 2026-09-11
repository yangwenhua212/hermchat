package com.eraherm.hermchat.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.HxmvConfig
import com.eraherm.hermchat.data.local.HxmvPrefsStore
import com.eraherm.hermchat.data.local.HxmvProvider
import com.eraherm.hermchat.data.network.HxmvApiClient
import com.eraherm.hermchat.data.network.HxmvArtifact
import com.eraherm.hermchat.data.network.HxmvUnauthorizedException
import com.eraherm.hermchat.util.UserFacingError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 一行任务进度：动作 · 状态 · 一行实测。 */
data class HxmvTaskLine(
    val action: String,
    val state: String,
    val detail: String,
)

data class HxmvUiState(
    val connected: Boolean = false,
    val statusLine: String = "未连接",
    val running: Boolean = false,
    val tasks: List<HxmvTaskLine> = emptyList(),
    val artifacts: List<HxmvArtifact> = emptyList(),
    val localInstanceFound: Boolean = false,
    val termuxInstalled: Boolean = false,
    val message: String? = null,
)

/**
 * HxMV 内容生产的页面状态机：发现实例 → 提交 → 跟事件 → 收成品（含主动推送/拉取两条路）。
 *
 * 远端与手机本机（Termux）同一套流程，只有 baseUrl 不同；切地址后重新探测即可。
 */
class HxmvViewModel(
    app: Application,
    private val prefs: HxmvPrefsStore,
    private val api: HxmvApiClient = HxmvApiClient(),
) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(HxmvUiState())
    val ui: StateFlow<HxmvUiState> = _ui.asStateFlow()

    val config: StateFlow<HxmvConfig> = prefs.config

    private var streamJob: Job? = null
    private var currentRunId: String? = null

    init {
        _ui.value = _ui.value.copy(termuxInstalled = isTermuxInstalled())
        checkConnection()
    }

    fun updateConfig(transform: (HxmvConfig) -> HxmvConfig) = prefs.update(transform)

    /** 探测当前配置的实例。 */
    fun checkConnection() {
        val cfg = prefs.config.value
        viewModelScope.launch {
            val line = runCatching {
                val health = api.health(cfg)
                val host = cfg.baseUrl.removePrefix("https://").removePrefix("http://")
                "已连接 $host · ${health.server}"
            }.getOrElse { UserFacingError.of(it, "连不上 HxMV") }
            _ui.value = _ui.value.copy(
                connected = line.startsWith("已连接"),
                statusLine = line,
            )
            detectLocalInstance()
        }
    }

    /** 本机模式：手机里自己跑着 HxMV（Termux）时自动发现，用户不用手填地址。 */
    private fun detectLocalInstance() {
        viewModelScope.launch {
            val found = runCatching {
                api.healthAt(HxmvConfig.LOCAL_BASE, prefs.config.value.token)
                true
            }.getOrDefault(false)
            _ui.value = _ui.value.copy(localInstanceFound = found)
        }
    }

    fun switchToLocalInstance() {
        prefs.update { it.copy(baseUrl = HxmvConfig.LOCAL_BASE) }
        checkConnection()
    }

    fun start(goal: String) {
        val trimmed = goal.trim()
        if (trimmed.isEmpty() || _ui.value.running) return
        streamJob?.cancel()
        _ui.value = _ui.value.copy(
            running = true, tasks = emptyList(), artifacts = emptyList(), message = null,
        )
        val cfg = prefs.config.value
        viewModelScope.launch {
            val runId = try {
                api.submit(cfg, trimmed, cfg.project.takeIf { it.isNotBlank() })
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(running = false, message = UserFacingError.of(e, "提交失败"))
                return@launch
            }
            currentRunId = runId
            streamJob = launch {
                runCatching {
                    api.stream(cfg, runId).collect { event ->
                        when (event.type) {
                            "task.start" -> upsertTask(event.action.orEmpty(), "进行中", "")
                            "critic" -> upsertTask(
                                event.action.orEmpty(),
                                if (event.taskState.isNullOrBlank()) "评审中" else decisionLabel(event.taskState),
                                event.detail.orEmpty(),
                            )
                            "decision" -> upsertTask(
                                event.action.orEmpty(),
                                decisionLabel(event.taskState),
                                event.detail.orEmpty(),
                            )
                            "run.done" -> {
                                val reused = event.reused ?: 0
                                _ui.value = _ui.value.copy(
                                    running = false,
                                    artifacts = event.artifacts,
                                    message = buildString {
                                        append(if (event.artifacts.isEmpty()) "完成" else "完成 · ${event.artifacts.size} 个成品")
                                        if (reused > 0) append(" · 复用 $reused 项")
                                    },
                                )
                            }
                        }
                    }
                }.onFailure { e ->
                    _ui.value = _ui.value.copy(
                        running = false,
                        message = UserFacingError.of(e, "连接中断"),
                    )
                }
            }
        }
    }

    private fun decisionLabel(decision: String?): String = when (decision?.uppercase()) {
        "PASS" -> "通过"
        "RETRY" -> "修正中"
        "FAIL" -> "失败"
        else -> "进行中"
    }

    private fun upsertTask(action: String, state: String, detail: String) {
        val label = actionLabel(action)
        val tasks = _ui.value.tasks.toMutableList()
        val index = tasks.indexOfFirst { it.action == label }
        val line = HxmvTaskLine(label, state, detail)
        if (index >= 0) tasks[index] = line else tasks += line
        _ui.value = _ui.value.copy(tasks = tasks)
    }

    private fun actionLabel(action: String): String = when (action) {
        "STORYBOARD" -> "分镜"
        "GENERATE_CHARACTER" -> "角色"
        "GENERATE_SCENE" -> "场景"
        "GENERATE_SHOT" -> "镜头"
        "COMPOSE" -> "成片"
        else -> action
    }

    /** 下载成片到缓存供本地播放（返回本地文件路径）。 */
    suspend fun downloadForPlay(artifact: HxmvArtifact): File? = withContext(Dispatchers.IO) {
        val runId = currentRunId ?: return@withContext null
        val cfg = prefs.config.value
        val url = artifact.url.takeIf { it.isNotBlank() }
            ?: api.artifactUrl(cfg, runId, artifact.name)
        val dest = File(getApplication<Application>().cacheDir, "hxmv/${artifact.name}")
        runCatching { api.download(cfg, artifact.copy(url = url), dest) }
            .onFailure { e ->
                _ui.value = _ui.value.copy(message = UserFacingError.of(e, "下载失败"))
            }
            .map { dest }
            .getOrNull()
    }

    fun saveToGallery(artifact: HxmvArtifact) {
        viewModelScope.launch {
            val file = downloadForPlay(artifact) ?: return@launch
            val ok = withContext(Dispatchers.IO) { HxmvExporter.toMovies(getApplication(), file) }
            _ui.value = _ui.value.copy(message = if (ok) "已存到相册" else "保存失败")
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    /** Termux 是否已装（"装到手机"的一步，安装本身要用户点）。 */
    fun isTermuxInstalled(): Boolean = runCatching {
        getApplication<Application>().packageManager
            .getPackageInfo("com.termux", 0)
        true
    }.getOrDefault(false)

    /** 手机本机跑 HxMV 的一键命令（复制给用户粘到 Termux）。 */
    fun termuxCommand(): String =
        "bash <(curl -sL https://raw.githubusercontent.com/yangwenhua212/HxMV/main/scripts/install-termux.sh)"

    fun showMessage(text: String) {
        _ui.value = _ui.value.copy(message = text)
    }

    companion object {
        fun factory(app: HermChatApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { HxmvViewModel(app, app.hxmvPrefsStore) }
        }
    }
}

/** 保存成品到系统相册（Android 10+ 用 MediaStore，免存储权限）。 */
object HxmvExporter {
    fun toMovies(context: Context, file: File): Boolean = runCatching {
        val isImage = file.name.endsWith(".png", ignoreCase = true) ||
            file.name.endsWith(".jpg", ignoreCase = true)
        val collection = if (isImage) {
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,
                if (isImage) "image/png" else "video/mp4")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Movies/HxSync")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().copyTo(out) } ?: return false
        true
    }.getOrDefault(false)
}
