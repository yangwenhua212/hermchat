package com.eraherm.hermchat.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.ChatAttachmentStore
import com.eraherm.hermchat.data.local.HxmvConfig
import com.eraherm.hermchat.data.local.HxmvPrefsStore
import com.eraherm.hermchat.data.local.HxmvProvider
import com.eraherm.hermchat.data.network.HxmvApiClient
import com.eraherm.hermchat.data.network.HxmvArtifact
import com.eraherm.hermchat.data.network.HxmvChatTurn
import com.eraherm.hermchat.data.network.HxmvConfigState
import com.eraherm.hermchat.data.network.HxmvRef
import com.eraherm.hermchat.data.network.HxmvRun
import com.eraherm.hermchat.util.UserFacingError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 一行任务进度：动作 · 状态 · 一行实测。 */
data class HxmvTaskLine(
    val action: String,
    val state: String,
    val detail: String,
)

data class HxmvChatLine(
    val role: String,          // "user" / "assistant"
    val text: String,
    val goal: String? = null,  // 非空 = 这一轮给出了可执行目标，界面出「开工」
)

data class HxmvUiState(
    val connected: Boolean = false,
    val statusLine: String = "未连接",
    val running: Boolean = false,
    val tasks: List<HxmvTaskLine> = emptyList(),
    val artifacts: List<HxmvArtifact> = emptyList(),
    val localInstanceFound: Boolean = false,
    val termuxInstalled: Boolean = false,
    /** 对话（只在本页内存里，退出即清——对话不需要长期留档） */
    val chat: List<HxmvChatLine> = emptyList(),
    val chatBusy: Boolean = false,
    /** 正在跑 / 刚跑完的任务号：不满意时拿它去删 */
    val currentRunId: String? = null,
    /** 实例要令牌但没填/填错 —— 页面要给一个「填令牌」的按钮，不能只说连不上。 */
    val needsToken: Boolean = false,
    val message: String? = null,
    /** 当前项目的参考图（图生视频的首帧）。 */
    val refs: List<HxmvRef> = emptyList(),
    val refBusy: Boolean = false,
    /** 作品库（/api/runs）：一次生产一条。 */
    val runs: List<HxmvRun> = emptyList(),
    val runsBusy: Boolean = false,
    /** 实例的接口配置（Key 状态 + 档位）；null = 还没拉到。 */
    val remoteConfig: HxmvConfigState? = null,
    val configBusy: Boolean = false,
)

/** 连接失败的兜底文案（映射表也兜到这里时，后面补真实原因）。 */
private const val CONNECT_FAILED = "连不上 HxMV"

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

    /** 选图上传参考图时复用聊天附件那套解码（HEIC/WebP 也转成 JPEG）。 */
    private val images = ChatAttachmentStore(app)

    /** 参考图字节按 url 缓存：网格里先加载一次，点开看大图不用再下一次。 */
    private val refCache = mutableMapOf<String, Bitmap>()

    private val _ui = MutableStateFlow(HxmvUiState())
    val ui: StateFlow<HxmvUiState> = _ui.asStateFlow()

    val config: StateFlow<HxmvConfig> = prefs.config

    private var streamJob: Job? = null
    private var currentRunId: String? = null

    init {
        _ui.value = _ui.value.copy(termuxInstalled = isTermuxInstalled())
        checkConnection()
        // 项目名变了就重拉参考图（打字时去抖，别每敲一个字发一次请求）
        viewModelScope.launch {
            prefs.config.map { it.project }.distinctUntilChanged().collectLatest {
                delay(700)
                loadRefs()
            }
        }
    }

    fun updateConfig(transform: (HxmvConfig) -> HxmvConfig) = prefs.update(transform)

    /** 拉作品库（进「作品」页时调）。 */
    fun loadRuns() {
        val cfg = prefs.config.value
        _ui.value = _ui.value.copy(runsBusy = true)
        viewModelScope.launch {
            try {
                val list = api.runs(cfg)
                _ui.value = _ui.value.copy(runs = list, runsBusy = false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(runsBusy = false)
            }
        }
    }

    /** 探测当前配置的实例。health 是公开接口，所以能分清「连不上」和「缺令牌」。 */
    fun checkConnection() {
        val cfg = prefs.config.value
        viewModelScope.launch {
            // 开源版不预填任何实例地址 → 没填就别探了，直接告诉他该填什么
            if (cfg.baseUrl.isBlank()) {
                _ui.value = _ui.value.copy(
                    statusLine = "先填你的 HxMV 实例地址（本机 http://127.0.0.1:8668，或你自己部署的域名）"
                )
                return@launch
            }
            val host = cfg.baseUrl.removePrefix("https://").removePrefix("http://")
            _ui.value = _ui.value.copy(statusLine = "正在连接 $host…")
            var result = runCatching { api.health(cfg) }
            if (result.isFailure) {
                // 移动网络到 CDN 的连接常被中途重置（实测：手机第一次没到服务器、隔一会儿那次就 200）
                // 自动再来一次，别让用户自己发现「再点一下就好了」
                delay(1200)
                result = runCatching { api.health(cfg) }
            }
            val health = result.getOrNull()
            val line = if (health == null) {
                val err = result.exceptionOrNull()
                val mapped = UserFacingError.of(err ?: IllegalStateException(), CONNECT_FAILED)
                // 映射表兜底时说明不了原因（网络中断/响应异常都糊成一句）→ 把真实原因带上
                if (mapped == CONNECT_FAILED && err != null) {
                    val why = err.message?.trim().orEmpty().ifBlank { err.javaClass.simpleName }
                    "$CONNECT_FAILED · ${why.take(40)}"
                } else mapped
            } else when {
                health.needsToken && cfg.token.isBlank() -> "已连接 $host · 需要令牌"
                health.needsToken && !health.authed -> "已连接 $host · 令牌不对"
                else -> "已连接 $host · ${health.server}"
            }
            val tokenProblem = health != null && health.needsToken && !health.authed
            _ui.value = _ui.value.copy(
                connected = health != null && !tokenProblem,
                statusLine = line,
                needsToken = tokenProblem,
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

    /** 手机部署第 ③ 步：手动检测本机（Termux 里）有没有 HxMV，有就直接切过去。 */
    fun probeLocalNow() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(message = "正在检测本机 HxMV…", termuxInstalled = isTermuxInstalled())
            val ok = runCatching {
                api.healthAt(HxmvConfig.LOCAL_BASE, prefs.config.value.token)
            }.isSuccess
            if (ok) {
                prefs.update { it.copy(baseUrl = HxmvConfig.LOCAL_BASE) }
                _ui.value = _ui.value.copy(localInstanceFound = true, message = "本机 HxMV 已就绪")
                checkConnection()
            } else {
                _ui.value = _ui.value.copy(localInstanceFound = false, message = "没检测到本机 HxMV")
            }
        }
    }

    /** 用户去装完 Termux 回来（页面重新可见）时刷新一下状态。 */
    fun refreshTermux() {
        val installed = isTermuxInstalled()
        if (installed != _ui.value.termuxInstalled) {
            _ui.value = _ui.value.copy(termuxInstalled = installed)
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
            currentRunId = null,
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
            _ui.value = _ui.value.copy(currentRunId = runId)
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

    /**
     * 对话：一句话 → HxMV 回话（可能带可执行目标）。
     * 这一步**不落盘、不开工、不烧钱**（老大要求：像聊天一样提需求，满意了再开工）。
     */
    fun sendChat(text: String) {
        val msg = text.trim()
        if (msg.isEmpty() || _ui.value.chatBusy) return
        val history = _ui.value.chat.map { HxmvChatTurn(it.role, it.text) }
        _ui.value = _ui.value.copy(
            chat = _ui.value.chat + HxmvChatLine("user", msg),
            chatBusy = true,
            message = null,
        )
        val cfg = prefs.config.value
        viewModelScope.launch {
            try {
                val reply = api.chat(cfg, msg, cfg.project.takeIf { it.isNotBlank() }, history)
                _ui.value = _ui.value.copy(
                    chat = _ui.value.chat + HxmvChatLine("assistant", reply.reply, reply.goal),
                    chatBusy = false,
                    message = if (reply.usedModel) null else "实例没配模型 Key：先按你的原话开工",
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    chatBusy = false,
                    message = UserFacingError.of(e, "对话失败"),
                )
            }
        }
    }

    /**
     * 不满意就删：删掉这次的产物，并撤销它在项目档案里的登记。
     * 不这么做的话，被否掉的片子还会继续当"设定"影响后面（老大要的就是别留）。
     */
    fun discard(runId: String) {
        if (runId.isBlank()) return
        val cfg = prefs.config.value
        viewModelScope.launch {
            try {
                val line = api.discard(cfg, runId)
                _ui.value = _ui.value.copy(
                    running = false,
                    currentRunId = null,
                    tasks = emptyList(),
                    artifacts = emptyList(),
                    message = line,
                )
                loadRuns()   // 删完顺手刷新作品库，别让被删的还挂在那
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(message = UserFacingError.of(e, "删除失败"))
            }
        }
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

    /** 拉当前项目的参考图清单（项目名变了会自动重拉）。 */
    fun loadRefs() {
        val cfg = prefs.config.value
        val project = cfg.project.trim()
        if (project.isEmpty()) {
            _ui.value = _ui.value.copy(refs = emptyList())
            return
        }
        viewModelScope.launch {
            val refs = runCatching { api.refs(cfg, project) }.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(refs = refs)
        }
    }

    /** 上传参考图：items = (图, 键名) 列表——单张和多张走同一条路（多张逐张传，带进度与汇总）。 */
    fun uploadRefs(items: List<Pair<Uri, String>>, kind: String) {
        val cfg = prefs.config.value
        val project = cfg.project.trim()
        if (project.isEmpty() || items.isEmpty() || _ui.value.refBusy) return
        val todo = items.map { (uri, key) -> uri to key.trim() }.filter { it.second.isNotEmpty() }
        if (todo.isEmpty()) return
        _ui.value = _ui.value.copy(refBusy = true, message = "正在上传 1/${todo.size}…")
        viewModelScope.launch {
            var ok = 0
            var firstError = ""
            todo.forEach { (uri, key) ->
                val bytes = withContext(Dispatchers.IO) { images.readJpeg(uri) }
                if (bytes == null || bytes.isEmpty()) {
                    if (firstError.isEmpty()) firstError = "有张图读不出来"
                    return@forEach
                }
                val result = runCatching { api.uploadRef(cfg, project, kind, key, bytes, "image/jpeg") }
                if (result.isSuccess) {
                    ok++
                    _ui.value = _ui.value.copy(message = "已上传 $ok/${todo.size}：$key")
                } else if (firstError.isEmpty()) {
                    firstError = "$key：" + UserFacingError.of(result.exceptionOrNull(), "上传失败")
                }
            }
            val failed = todo.size - ok
            _ui.value = _ui.value.copy(
                refBusy = false,
                message = "已上传 $ok 张" + if (failed > 0) "，失败 $failed 张 —— $firstError" else "",
            )
            if (ok > 0) {
                // 同一个 key 重新上传时 url 不变 → 必须清缓存，否则网格还显示旧图
                refCache.clear()
                loadRefs()
            }
        }
    }

    /** 注销一张参考图（档案里摘掉）。 */
    fun deleteRef(ref: HxmvRef) {
        val cfg = prefs.config.value
        val project = cfg.project.trim()
        if (project.isEmpty() || _ui.value.refBusy) return
        _ui.value = _ui.value.copy(refBusy = true)
        viewModelScope.launch {
            val result = runCatching { api.deleteRef(cfg, project, ref) }
            val message = result.fold(
                onSuccess = { removed -> if (removed) "已删除 ${ref.name}" else "档案里没有这张" },
                onFailure = { UserFacingError.of(it, "删除失败") },
            )
            _ui.value = _ui.value.copy(refBusy = false, message = message)
            if (result.getOrDefault(false)) refCache.clear()
            loadRefs()
        }
    }

    /** 参考图缩略图（清单里一眼看出用的是哪张图）；同一张只下一次，点开大图直接复用。 */
    suspend fun refImage(ref: HxmvRef): Bitmap? = withContext(Dispatchers.IO) {
        if (ref.url.isBlank()) return@withContext null
        refCache[ref.url]?.let { return@withContext it }
        runCatching {
            val bytes = api.fetchBytes(prefs.config.value, ref.url)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.also { refCache[ref.url] = it }
    }

    /** 拉实例的接口配置（Key 是否配好 + 当前档位）。 */
    fun loadRemoteConfig() {
        if (_ui.value.configBusy) return
        _ui.value = _ui.value.copy(configBusy = true)
        viewModelScope.launch {
            val result = runCatching { api.configState(prefs.config.value) }
            _ui.value = _ui.value.copy(
                configBusy = false,
                remoteConfig = result.getOrNull() ?: _ui.value.remoteConfig,
                message = result.exceptionOrNull()?.let { UserFacingError.of(it, "读不到接口配置") },
            )
        }
    }

    /** 保存 Key / 切档位（服务端立即生效，不用重启实例）。 */
    fun saveRemoteConfig(key: String, videoModel: String, vlmModel: String) {
        if (_ui.value.configBusy) return
        _ui.value = _ui.value.copy(configBusy = true)
        viewModelScope.launch {
            val result = runCatching {
                api.saveConfig(prefs.config.value, key, videoModel, vlmModel)
            }
            _ui.value = _ui.value.copy(
                configBusy = false,
                remoteConfig = result.getOrNull() ?: _ui.value.remoteConfig,
                message = result.fold({ "已保存" }, { UserFacingError.of(it, "保存失败") }),
            )
        }
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
