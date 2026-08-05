package com.eraherm.hermchat.data.local

import android.content.Context
import android.net.Uri
import com.eraherm.hermchat.data.network.SharedHttpClients
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class WallpaperEntry(
    val id: String,
    val label: String,
    val keywords: List<String>,
    val url: String,
    val source: String = "preset",
)

/**
 * 聊天背景图：相册拷贝、精选预设下载、维基共享资源搜索下载。
 */
class WallpaperStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "chat_bg").also { it.mkdirs() }
    private val client = SharedHttpClients.download.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val stopFlag = AtomicBoolean(false)

    fun fileFor(id: String): File = File(root, sanitize(id) + ".img")

    fun isDownloaded(id: String): Boolean {
        val f = fileFor(id)
        return f.exists() && f.length() > MIN_BYTES
    }

    fun localPath(id: String): String? =
        fileFor(id).takeIf { it.exists() && it.length() > MIN_BYTES }?.absolutePath

    fun searchLocal(query: String): List<WallpaperEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return PRESETS
        return PRESETS.filter { entry ->
            entry.label.lowercase().contains(q) ||
                entry.keywords.any { it.lowercase().contains(q) } ||
                entry.id.lowercase().contains(q)
        }
    }

    /** 本地精选 + Wikimedia Commons 在线结果。 */
    fun search(query: String): Result<List<WallpaperEntry>> = runCatching {
        val local = searchLocal(query)
        val online = if (query.trim().length >= 2) {
            searchWikimedia(query.trim()).getOrDefault(emptyList())
        } else {
            emptyList()
        }
        (local + online).distinctBy { it.id }.take(24)
    }

    fun importFromUri(uri: Uri): Result<File> = runCatching {
        val dest = File(root, "custom_${System.currentTimeMillis()}.img")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取图片")
        if (dest.length() < MIN_BYTES) {
            dest.delete()
            error("图片无效")
        }
        dest
    }

    fun download(entry: WallpaperEntry): Result<File> = runCatching {
        stopFlag.set(false)
        val dest = fileFor(entry.id)
        if (dest.exists() && dest.length() > MIN_BYTES) return@runCatching dest
        val tmp = File(dest.parentFile, "${dest.name}.part")
        tmp.delete()
        val request = Request.Builder()
            .url(entry.url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("下载失败 HTTP ${response.code}")
            val body = response.body ?: error("空响应")
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (stopFlag.get()) {
                            tmp.delete()
                            error("已取消")
                        }
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                    }
                }
            }
        }
        if (tmp.length() < MIN_BYTES) {
            tmp.delete()
            error("图片不完整")
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        dest
    }

    fun cancelDownload() {
        stopFlag.set(true)
    }

    fun clearCustomCopies() {
        root.listFiles()?.forEach { file ->
            if (file.name.startsWith("custom_")) file.delete()
        }
    }

    private fun searchWikimedia(query: String): Result<List<WallpaperEntry>> = runCatching {
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        val api =
            "https://commons.wikimedia.org/w/api.php?action=query&format=json&origin=*" +
                "&generator=search&gsrnamespace=6&gsrlimit=12&gsrsearch=${encoded}" +
                "&prop=imageinfo&iiprop=url|mime|size&iiurlwidth=1080"
        val request = Request.Builder()
            .url(api)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(response.body?.string().orEmpty())
            val pages = json.optJSONObject("query")?.optJSONObject("pages")
                ?: return@runCatching emptyList()
            buildList {
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val page = pages.getJSONObject(keys.next())
                    val title = page.optString("title")
                        .removePrefix("File:")
                        .substringBeforeLast('.')
                        .take(28)
                    val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                    val mime = info.optString("mime")
                    if (!mime.startsWith("image/")) continue
                    if (mime.contains("svg")) continue
                    val url = info.optString("thumburl").ifBlank { info.optString("url") }
                    if (url.isBlank()) continue
                    val size = info.optLong("size", 0L)
                    if (size > 0L && size > 12L * 1024L * 1024L) continue
                    val id = "wiki_${page.optInt("pageid")}"
                    add(
                        WallpaperEntry(
                            id = id,
                            label = title.ifBlank { id },
                            keywords = listOf(query),
                            url = url,
                            source = "wikimedia",
                        ),
                    )
                }
            }
        }
    }

    private fun sanitize(id: String): String =
        id.replace(Regex("""[^A-Za-z0-9._-]"""), "_").take(64)

    companion object {
        private const val MIN_BYTES = 2_000L
        private const val USER_AGENT =
            "HxSync/0.1 (https://github.com/yangwenhua212/hermchat; Android wallpaper)"

        val PRESETS: List<WallpaperEntry> = listOf(
            WallpaperEntry(
                id = "unsplash-forest",
                label = "森林晨光",
                keywords = listOf("绿", "森林", "自然", "默认"),
                url = "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1080&q=80&auto=format",
            ),
            WallpaperEntry(
                id = "unsplash-sky",
                label = "晴空",
                keywords = listOf("蓝", "天空", "浅色"),
                url = "https://images.unsplash.com/photo-1419242902214-272b3f66ee7a?w=1080&q=80&auto=format",
            ),
            WallpaperEntry(
                id = "unsplash-sea",
                label = "海边",
                keywords = listOf("蓝", "海", "水"),
                url = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1080&q=80&auto=format",
            ),
            WallpaperEntry(
                id = "unsplash-mist",
                label = "山雾",
                keywords = listOf("雾", "山", "浅色"),
                url = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1080&q=80&auto=format",
            ),
            WallpaperEntry(
                id = "unsplash-cloud",
                label = "云层",
                keywords = listOf("白", "云", "浅色"),
                url = "https://images.unsplash.com/photo-1534088568595-a066f410bcda?w=1080&q=80&auto=format",
            ),
            WallpaperEntry(
                id = "unsplash-leaf",
                label = "叶绿",
                keywords = listOf("绿", "叶", "清新"),
                url = "https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=1080&q=80&auto=format",
            ),
            WallpaperEntry(
                id = "unsplash-dawn",
                label = "清晨",
                keywords = listOf("橙", "晨", "浅色"),
                url = "https://images.unsplash.com/photo-1495616811223-4d98c6e9c869?w=1080&q=80&auto=format",
            ),
            WallpaperEntry(
                id = "unsplash-stone",
                label = "浅岩",
                keywords = listOf("灰", "岩", "浅色"),
                url = "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1080&q=80&auto=format",
            ),
        )
    }
}
