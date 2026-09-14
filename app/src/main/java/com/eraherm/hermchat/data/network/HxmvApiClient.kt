package com.eraherm.hermchat.data.network

import android.util.Base64
import com.eraherm.hermchat.data.local.HxmvConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 实例自检（GET /api/health）：客户端"发现 HxMV"就看这一个接口。 */
data class HxmvHealth(
    val version: String,
    val server: String,
    val ffmpeg: Boolean,
    val providers: Map<String, Boolean>,
    val projects: List<String>,
    val needsToken: Boolean,
    /** 令牌是否被实例接受（health 是公开接口，靠这个字段区分"没填/填错"）。 */
    val authed: Boolean,
)

/** 一个可下载的成品。url 由客户端自己按 base+runId+name 拼，不在推送里夹带凭据。 */
data class HxmvArtifact(
    val name: String,
    val kind: String,
    val size: Long,
    val url: String,
) {
    val label: String
        get() = when (kind) {
            "final" -> "成片"
            "asset" -> "参考图"
            else -> "镜头"
        }
}

/** 事件（服务端 SSE 与历史回放同构）。 */
data class HxmvEvent(
    val type: String,
    val action: String? = null,
    val taskState: String? = null,
    val detail: String? = null,
    val attempts: Int? = null,
    val costUnits: Double? = null,
    val artifacts: List<HxmvArtifact> = emptyList(),
    val reused: Int? = null,
    val text: String? = null,
)

class HxmvUnauthorizedException : IOException("令牌不对或未设置")

/** 项目里的一张参考图（图生视频的首帧）。[url] 是相对地址，客户端补 baseUrl + token 取字节。 */
data class HxmvRef(
    val key: String,
    val name: String,
    val kind: String,
    val exists: Boolean,
    val size: Long,
    val url: String,
) {
    val kindLabel: String get() = if (kind == "scene") "场景" else "角色"
}

/** 实例的接口配置状态（Key 只回脱敏串，明文永不回传）。 */
data class HxmvConfigState(
    val keyConfigured: Boolean,
    val keyMasked: String,
    val videoModel: String,
    val vlmModel: String,
    val visionReady: Boolean,
    val visionModel: String,
)

/**
 * 对话一轮（App → HxMV）：`goal` 非空表示这一轮它给了可执行目标，界面出「开工」按钮。
 * `usedModel=false` = 实例没配模型 Key，这时候 goal 就是用户原话（可跑性是底线）。
 */
data class HxmvChatTurn(val role: String, val text: String)

data class HxmvChatReply(
    val reply: String,
    val goal: String?,
    val usedModel: Boolean,
)

/**
 * HxMV 客户端。远端与本机（Termux）走同一套接口，只有 baseUrl 不同。
 *
 * 接口（见 HxMV 仓库 docs/CLIENT.md）：
 *   GET  /api/health                       发现实例 + 能力
 *   POST /api/run {goal,provider,project}  提交生产 → run_id
 *   GET  /api/stream?run_id=&token=        SSE 实时事件
 *   GET  /api/artifact?run_id=&name=       取产物
 *   POST /api/chat {message,project,history}  对话：回话 + 可执行目标（不落盘、不开工）
 *   POST /api/discard {run_id}             不满意就删：删产物 + 撤销档案登记
 *   GET  /api/ref?project=                 项目参考图清单（角色/场景）
 *   POST /api/ref {project,kind,key,image} 上传参考图（图生视频的首帧）
 *   DELETE /api/ref?project=&kind=&key=    注销参考图
 *   GET  /api/config                       接口配置状态（Key 只回脱敏串）
 *   POST /api/config {provider,key?,…}     保存 Key / 切档位（立即生效）
 */
class HxmvApiClient(
    private val client: OkHttpClient = SharedHttpClients.streamingApi(),
    private val io: OkHttpClient = SharedHttpClients.api,
    /**
     * 探测专用（连 8s / 读 12s）：健康检查不能拿「连 15s + 读 60s」去晾用户——
     * 真机实测过「点了检测几分钟没反应」，其实是一直在等超时。失败后调用方会重试一次。
     */
    private val probe: OkHttpClient = SharedHttpClients.connectionTest(),
) {
    suspend fun health(config: HxmvConfig): HxmvHealth {
        val json = JSONObject(get(config, "/api/health", probe))
        val providers = mutableMapOf<String, Boolean>()
        json.optJSONObject("providers")?.let { obj ->
            obj.keys().forEach { key -> providers[key] = obj.optJSONObject(key)?.optBoolean("ready") == true }
        }
        val projects = mutableListOf<String>()
        json.optJSONArray("projects")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { projects += it }
            }
        }
        return HxmvHealth(
            version = json.optString("version"),
            server = json.optString("server"),
            ffmpeg = json.optBoolean("ffmpeg"),
            providers = providers,
            projects = projects,
            needsToken = json.optBoolean("needs_token"),
            authed = json.optBoolean("authed"),
        )
    }

    /** 不改动本地配置地探一个地址（用于"检测连接"和本机模式探测）。 */
    suspend fun healthAt(baseUrl: String, token: String): HxmvHealth =
        health(HxmvConfig(baseUrl = baseUrl.trim().trimEnd('/'), token = token.trim()))

    suspend fun submit(config: HxmvConfig, goal: String, project: String?): String {
        val body = JSONObject()
            .put("goal", goal)
            .put("provider", config.provider.wire)
            .put("project", project.orEmpty())
        val json = JSONObject(post(config, "/api/run", body.toString()))
        return json.optString("run_id").takeIf { it.isNotBlank() }
            ?: throw IOException("提交未返回任务号")
    }

    /** SSE：先回放已落盘事件，再实时推送；连接结束即 flow 结束。 */
    fun stream(config: HxmvConfig, runId: String): Flow<HxmvEvent> = callbackFlow {
        val req = Request.Builder()
            .url(withToken("${config.baseUrl}/api/stream?run_id=$runId", config.token))
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val call = client.newCall(req)
        val reader = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (response.code == 401) throw HxmvUnauthorizedException()
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val source = response.body?.source() ?: throw IOException("空响应")
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty()) continue
                        runCatching { parseEvent(JSONObject(payload), runId, config) }
                            .getOrNull()?.let { trySend(it) }
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose {
            call.cancel()
            reader.cancel()
        }
    }

    /**
     * 对话：一句话 → 回话 + 可执行目标（`goal` 非空时界面给「开工」）。
     * 服务端**不落盘、不开工**，所以这一步可以随便聊，不烧钱。
     */
    suspend fun chat(
        config: HxmvConfig,
        message: String,
        project: String?,
        history: List<HxmvChatTurn>,
    ): HxmvChatReply {
        val arr = JSONArray()
        history.forEach { turn ->
            arr.put(JSONObject().put("role", turn.role).put("content", turn.text))
        }
        val body = JSONObject()
            .put("message", message)
            .put("project", project.orEmpty())
            .put("history", arr)
        val json = JSONObject(post(config, "/api/chat", body.toString()))
        val goal = json.optString("goal").takeIf { it.isNotBlank() && it != "null" }
        return HxmvChatReply(
            reply = json.optString("reply").ifBlank { "（没听清，再说一次？）" },
            goal = goal,
            usedModel = json.optBoolean("llm", false),
        )
    }

    /** 不满意就删：删产物 + 撤销它在项目档案里的登记。回一句给人看的结果。 */
    suspend fun discard(config: HxmvConfig, runId: String): String {
        val body = JSONObject().put("run_id", runId)
        val json = JSONObject(post(config, "/api/discard", body.toString()))
        return "已删 ${json.optInt("removed_files")} 个文件，" +
            "撤销 ${json.optInt("removed_records")} 条登记"
    }

    suspend fun download(config: HxmvConfig, artifact: HxmvArtifact, dest: File) {
        val req = Request.Builder().url(withToken(artifact.url, config.token)).get().build()
        val call = io.newBuilder().readTimeout(5, TimeUnit.MINUTES).build().newCall(req)
        call.execute().use { response ->
            if (response.code == 401) throw HxmvUnauthorizedException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("空响应")
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    fun artifactUrl(config: HxmvConfig, runId: String, name: String): String =
        "${config.baseUrl}/api/artifact?run_id=$runId&name=$name"

    /** 项目参考图清单（角色 + 场景）。 */
    suspend fun refs(config: HxmvConfig, project: String): List<HxmvRef> {
        if (project.isBlank()) return emptyList()
        val json = JSONObject(get(config, "/api/ref?project=" + enc(project)))
        val out = mutableListOf<HxmvRef>()
        for ((bucket, kind) in listOf("characters" to "character", "scenes" to "scene")) {
            val arr = json.optJSONArray(bucket) ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val key = obj.optString("key")
                if (key.isBlank()) continue
                out += HxmvRef(
                    key = key,
                    name = obj.optString("name").takeIf { it.isNotBlank() } ?: key,
                    kind = kind,
                    exists = obj.optBoolean("exists"),
                    size = obj.optLong("size"),
                    url = obj.optString("url"),
                )
            }
        }
        return out
    }

    /**
     * 上传一张参考图（图生视频的首帧）。
     *
     * 图片走 base64 JSON（服务端 ≤12MB，JPEG/PNG）；mode=auto 时设定表会自动裁上部主视觉。
     * 返回一句给人看的摘要。
     */
    suspend fun uploadRef(
        config: HxmvConfig,
        project: String,
        kind: String,
        key: String,
        bytes: ByteArray,
        mime: String?,
    ): String {
        val type = (mime ?: "").takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val body = JSONObject()
            .put("project", project)
            .put("kind", kind)
            .put("key", key)
            .put("mode", "auto")
            .put("image", "data:$type;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
        val json = JSONObject(post(config, "/api/ref", body.toString()))
        val saved = json.optString("key").takeIf { it.isNotBlank() } ?: key
        return "已存为 $saved"
    }

    /** 注销一张参考图（档案里摘掉 + 删文件）。 */
    suspend fun deleteRef(config: HxmvConfig, project: String, ref: HxmvRef): Boolean {
        val path = "/api/ref?project=${enc(project)}&kind=${ref.kind}&key=${enc(ref.key)}"
        return JSONObject(del(config, path)).optBoolean("removed")
    }

    /** 接口配置状态：Key 是否配好（脱敏串）+ 当前档位 + 视觉评审是否就绪。 */
    suspend fun configState(config: HxmvConfig): HxmvConfigState {
        val json = JSONObject(get(config, "/api/config"))
        val opts = json.optJSONObject("options") ?: JSONObject()
        val vision = json.optJSONObject("vision") ?: JSONObject()
        var configured = false
        var masked = ""
        json.optJSONArray("providers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("id") != "zhipu") continue
                configured = obj.optBoolean("configured")
                masked = obj.optString("masked")
            }
        }
        return HxmvConfigState(
            keyConfigured = configured,
            keyMasked = masked,
            videoModel = opts.optString("video_model"),
            vlmModel = opts.optString("vlm_model"),
            visionReady = vision.optBoolean("ready"),
            visionModel = vision.optString("model"),
        )
    }

    /** 保存 Key / 切档位（服务端写进实例本机配置，立即生效）。返回保存后的完整状态。 */
    suspend fun saveConfig(
        config: HxmvConfig,
        key: String,
        videoModel: String,
        vlmModel: String,
    ): HxmvConfigState {
        val body = JSONObject().put("provider", "zhipu")
        if (key.isNotBlank()) body.put("key", key.trim())
        if (videoModel.isNotBlank()) body.put("video_model", videoModel)
        if (vlmModel.isNotBlank()) body.put("vlm_model", vlmModel)
        post(config, "/api/config", body.toString())
        return configState(config)
    }

    /** 取参考图字节（清单缩略图用）。[path] 是清单里给的相对地址。 */
    suspend fun fetchBytes(config: HxmvConfig, path: String): ByteArray {
        val req = Request.Builder().url(withToken(config.baseUrl + path, config.token)).get().build()
        return io.newCall(req).execute().use { response ->
            if (response.code == 401) throw HxmvUnauthorizedException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun enc(text: String): String = java.net.URLEncoder.encode(text, "UTF-8")

    private fun withToken(url: String, token: String): String {
        if (token.isBlank()) return url
        val sep = if (url.contains('?')) "&" else "?"
        return "$url$sep" + "token=${java.net.URLEncoder.encode(token, "UTF-8")}"
    }

    private fun parseEvent(json: JSONObject, runId: String, config: HxmvConfig): HxmvEvent? {
        val type = json.optString("type")
        if (type.isBlank()) return null
        val artifacts = mutableListOf<HxmvArtifact>()
        json.optJSONArray("artifacts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                if (name.isBlank()) continue
                artifacts += HxmvArtifact(
                    name = name,
                    kind = obj.optString("kind", "shot"),
                    size = obj.optLong("size"),
                    url = obj.optString("url").takeIf { it.isNotBlank() }
                        ?: artifactUrl(config, runId, name),
                )
            }
        }
        return HxmvEvent(
            type = type,
            action = json.optString("action").takeIf { it.isNotBlank() },
            taskState = json.optString("decision").takeIf { it.isNotBlank() },
            detail = metricsLine(json),
            attempts = json.optInt("attempts").takeIf { json.has("attempts") },
            costUnits = json.optDouble("cost_units").takeIf { json.has("cost_units") },
            artifacts = artifacts,
            reused = json.optInt("n_reused").takeIf { json.has("n_reused") },
            text = json.optString("text").takeIf { it.isNotBlank() },
        )
    }

    /** 一行实测指标：分辨率 · 时长 · 一致度（不堆技术细节）。 */
    private fun metricsLine(json: JSONObject): String? {
        val measured = json.optJSONObject("measured")?.optJSONObject("metrics") ?: return null
        val parts = mutableListOf<String>()
        val w = measured.optInt("width")
        val h = measured.optInt("height")
        if (w > 0 && h > 0) parts += "${w}x$h"
        measured.optDouble("duration").takeIf { it > 0 }?.let { parts += String.format("%.1f秒", it) }
        measured.optDouble("consistency").takeIf { it > 0 }?.let {
            parts += String.format("一致度%.2f", it)
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun get(config: HxmvConfig, path: String, client: OkHttpClient = io): String {
        val req = Request.Builder()
            .url(withToken(config.baseUrl + path, config.token))
            .get()
            .build()
        return client.newCall(req).execute().use { response ->
            if (response.code == 401) throw HxmvUnauthorizedException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    private fun post(config: HxmvConfig, path: String, body: String): String {
        val req = Request.Builder()
            .url(config.baseUrl + path)
            .header("X-Hxmv-Token", config.token)
            .post(body.toRequestBody(JSON))
            .build()
        return io.newCall(req).execute().use { response ->
            if (response.code == 401) throw HxmvUnauthorizedException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    /** DELETE 不带体（参数走 query），令牌只能走头或 query——两种都带上更稳。 */
    private fun del(config: HxmvConfig, path: String): String {
        val req = Request.Builder()
            .url(withToken(config.baseUrl + path, config.token))
            .header("X-Hxmv-Token", config.token)
            .delete()
            .build()
        return io.newCall(req).execute().use { response ->
            if (response.code == 401) throw HxmvUnauthorizedException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
