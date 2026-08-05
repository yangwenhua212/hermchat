package com.eraherm.hermchat.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 打开已安装应用（须确认）。支持常用中文名别名或 packageName。
 */
class AppOpenTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val app = call.arguments["app"]?.trim().orEmpty()
            .ifBlank { call.arguments["name"]?.trim().orEmpty() }
        val pkgArg = call.arguments["package"]?.trim().orEmpty()
            .ifBlank { call.arguments["packageName"]?.trim().orEmpty() }
        if (app.isBlank() && pkgArg.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少应用名或包名")
        }
        val packageName = resolvePackage(pkgArg, app)
            ?: return@withContext ToolResult(
                call.id,
                name,
                false,
                "找不到应用「${app.ifBlank { pkgArg }}」，请确认已安装或换包名",
            )
        return@withContext try {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: return@withContext ToolResult(call.id, name, false, "该应用无法启动")
            context.startActivity(launch)
            val label = appLabel(packageName) ?: packageName
            ToolResult(call.id, name, true, "已打开「$label」")
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "无法打开应用")
        }
    }

    private fun resolvePackage(explicit: String, alias: String): String? {
        if (explicit.isNotBlank()) {
            if (isInstalled(explicit)) return explicit
        }
        val key = alias.trim().lowercase().replace(" ", "")
        if (key.isBlank()) return null
        ALIASES[key]?.firstOrNull { isInstalled(it) }?.let { return it }
        // 已装应用按标签模糊匹配（有限）
        return findByLabel(alias)
    }

    private fun isInstalled(packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    private fun appLabel(packageName: String): String? = runCatching {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    }.getOrNull()

    private fun findByLabel(alias: String): String? {
        val needle = alias.trim()
        if (needle.length < 2) return null
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, PackageManager.MATCH_DEFAULT_ONLY)
        val exact = apps.firstOrNull {
            it.loadLabel(pm).toString().equals(needle, ignoreCase = true)
        }
        if (exact != null) return exact.activityInfo.packageName
        val partial = apps.firstOrNull {
            it.loadLabel(pm).toString().contains(needle, ignoreCase = true)
        }
        return partial?.activityInfo?.packageName
    }

    companion object {
        const val NAME = "app.open"

        /** 别名 → 候选包名（按常见度排序） */
        val ALIASES: Map<String, List<String>> = mapOf(
            "微信" to listOf("com.tencent.mm"),
            "wechat" to listOf("com.tencent.mm"),
            "支付宝" to listOf("com.eg.android.AlipayGphone"),
            "alipay" to listOf("com.eg.android.AlipayGphone"),
            "淘宝" to listOf("com.taobao.taobao"),
            "抖音" to listOf(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.lite",
            ),
            "douyin" to listOf(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.lite",
            ),
            "抖音极速版" to listOf("com.ss.android.ugc.aweme.lite"),
            "设置" to listOf("com.android.settings"),
            "settings" to listOf("com.android.settings"),
            "相机" to listOf(
                "com.android.camera",
                "com.android.camera2",
                "com.huawei.camera",
                "com.sec.android.app.camera",
                "com.miui.camera",
            ),
            "相册" to listOf(
                "com.google.android.apps.photos",
                "com.android.gallery3d",
                "com.miui.gallery",
                "com.sec.android.gallery3d",
            ),
            "浏览器" to listOf(
                "com.android.chrome",
                "com.android.browser",
                "com.huawei.browser",
                "com.mi.globalbrowser",
            ),
            "地图" to listOf(
                "com.autonavi.minimap",
                "com.baidu.BaiduMap",
                "com.google.android.apps.maps",
            ),
            "高德" to listOf("com.autonavi.minimap"),
            "百度地图" to listOf("com.baidu.BaiduMap"),
            "时钟" to listOf(
                "com.google.android.deskclock",
                "com.android.deskclock",
                "com.sec.android.app.clockpackage",
            ),
            "电话" to listOf(
                "com.google.android.dialer",
                "com.android.dialer",
                "com.samsung.android.dialer",
                "com.android.contacts",
            ),
            "短信" to listOf(
                "com.google.android.apps.messaging",
                "com.android.mms",
                "com.samsung.android.messaging",
            ),
        )
    }
}
