package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import java.util.UUID

/**
 * 「打开 DeepSeek 官网 / 打开 https://…」→ [OpenUrlTool]；
 * 未知站点 → [WebSearchTool]（带 follow_up=url.open，搜完自动开链）。
 *
 * 注意：与 [LocalAppOpenPlanner] 互斥——「打开抖音/淘宝」等已有 App 别名且未提官网时
 * 必须返回 null，否则会抢先打开网页。
 */
object LocalUrlOpenPlanner {
    /** 常见站点别名 → 完整 URL（小写 key）。 */
    private val KNOWN_SITES: List<Pair<List<String>, String>> = listOf(
        listOf("deepseek", "深度求索") to "https://www.deepseek.com/",
        listOf("openai", "chatgpt") to "https://chatgpt.com/",
        listOf("sora") to "https://openai.com/sora",
        listOf("github") to "https://github.com/",
        listOf("google", "谷歌") to "https://www.google.com/",
        listOf("baidu", "百度") to "https://www.baidu.com/",
        listOf("bing", "必应") to "https://www.bing.com/",
        listOf("bilibili", "哔哩哔哩", "b站") to "https://www.bilibili.com/",
        listOf("zhihu", "知乎") to "https://www.zhihu.com/",
        listOf("douyin", "抖音") to "https://www.douyin.com/",
        listOf("taobao", "淘宝") to "https://www.taobao.com/",
        listOf("jd", "京东") to "https://www.jd.com/",
        listOf("weibo", "微博") to "https://weibo.com/",
    )

    const val FOLLOW_UP_URL_OPEN = "url.open"

    private val URL_IN_TEXT = Regex(
        """https?://[^\s<>"'）】]+""",
        RegexOption.IGNORE_CASE,
    )
    private val BARE_DOMAIN = Regex(
        """(?i)\b([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+)\b""",
    )
    private val OPEN_SITE = Regex(
        """(?:帮我|请)?(?:打开|访问|进入)\s*(.+?)(?:的)?(?:官网|网站|主页|网页)?\s*$""",
    )

    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', '。', '，', ')', '）')?.let { url ->
            if (looksOpenIntent(text) || text == url) {
                return openCall(url)
            }
        }
        if (!looksOpenIntent(text)) return null
        val target = OPEN_SITE.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            .removeSuffix("一下")
            .trim()
        if (target.isBlank()) return null
        if (target.contains("搜索") || target.contains("查一下") || target.contains("搜一下")) {
            return null
        }
        val wantsWebsite = wantsWebsiteIntent(text, target)
        val stripped = target
            .replace(Regex("(?:的)?(?:官网|网站|主页|网页)$"), "")
            .trim()
        // 「打开抖音 / 打开抖音app」：有 App 别名且未提官网 → 交给 LocalAppOpenPlanner
        if (!wantsWebsite && matchesInstallableApp(stripped.ifBlank { target })) {
            return null
        }
        if (wantsWebsite) {
            resolveKnown(stripped.ifBlank { target })?.let { return openCall(it) }
            resolveKnown(target)?.let { return openCall(it) }
            resolveKnown(text)?.let { return openCall(it) }
        } else {
            // 无官网词：仅打开「无对应 App」的已知站（如 DeepSeek）或裸域名
            if (!matchesInstallableApp(stripped.ifBlank { target })) {
                resolveKnown(target)?.let { return openCall(it) }
                if (stripped.isNotBlank() && stripped != target) {
                    resolveKnown(stripped)?.let { return openCall(it) }
                }
            }
        }
        val domainSource = stripped.ifBlank { target }
        val domain = BARE_DOMAIN.find(domainSource)?.groupValues?.getOrNull(1)
        if (domain != null && looksLikeWebsiteDomain(domain)) {
            return openCall("https://$domain/")
        }
        if (wantsWebsite) {
            val name = productNameForSearch(stripped.ifBlank { target }, text)
            if (name.isNotBlank()) {
                return searchThenOpenCall(name)
            }
        }
        return null
    }

    private fun wantsWebsiteIntent(text: String, target: String): Boolean =
        text.contains("官网") ||
            text.contains("网站") ||
            text.contains("主页") ||
            text.contains("网页") ||
            target.contains("官网") ||
            target.contains("网站") ||
            target.contains("主页") ||
            target.contains("网页")

    /** 与 [AppOpenTool.ALIASES] 重叠的名字（含抖音/淘宝等）勿抢 url.open。 */
    private fun matchesInstallableApp(raw: String): Boolean {
        val key = raw.trim()
            .removeSuffix("一下")
            .removeSuffix("App")
            .removeSuffix("APP")
            .removeSuffix("app")
            .removeSuffix("应用")
            .trim()
            .lowercase()
        if (key.isBlank()) return false
        return AppOpenTool.ALIASES.keys.any { alias ->
            val a = alias.lowercase()
            key == a || key.contains(a) || a.contains(key)
        }
    }

    private fun productNameForSearch(stripped: String, full: String): String {
        var name = stripped
            .replace(Regex("(?:帮我|请|打开|访问|进入)"), "")
            .replace(Regex("(?:的)?(?:官网|网站|主页|网页)"), "")
            .trim()
        if (name.isBlank()) {
            name = full
                .replace(Regex("(?:帮我|请|打开|访问|进入)"), "")
                .replace(Regex("(?:的)?(?:官网|网站|主页|网页)"), "")
                .trim()
        }
        return name.take(40)
    }

    private fun looksOpenIntent(text: String): Boolean =
        text.contains("打开") ||
            text.contains("访问") ||
            text.contains("进入") ||
            URL_IN_TEXT.containsMatchIn(text)

    private fun resolveKnown(raw: String): String? {
        val lower = raw.lowercase()
        for ((aliases, url) in KNOWN_SITES) {
            if (aliases.any { alias -> lower.contains(alias) }) return url
        }
        return null
    }

    private fun looksLikeWebsiteDomain(domain: String): Boolean {
        val d = domain.lowercase()
        if (d.count { it == '.' } < 1) return false
        if (d.matches(Regex("""\d+(\.\d+)+"""))) return false
        val tld = d.substringAfterLast('.')
        return tld.length in 2..24 && tld.all { it.isLetter() }
    }

    private fun openCall(url: String): ToolCall {
        val normalized = url.trim()
        val args = mapOf("url" to normalized)
        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = OpenUrlTool.NAME,
            arguments = args,
            needConfirm = false,
            title = "打开链接",
            summary = ToolCallParser.summarize(OpenUrlTool.NAME, args),
        )
    }

    private fun searchThenOpenCall(productName: String): ToolCall {
        val query = "${productName.trim()} 官网"
        val args = mapOf(
            "query" to query,
            "follow_up" to FOLLOW_UP_URL_OPEN,
        )
        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = WebSearchTool.NAME,
            arguments = args,
            needConfirm = false,
            title = "搜索官网",
            summary = ToolCallParser.summarize(WebSearchTool.NAME, mapOf("query" to query)),
        )
    }
}
