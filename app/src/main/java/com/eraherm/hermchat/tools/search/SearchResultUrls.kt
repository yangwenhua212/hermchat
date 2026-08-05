package com.eraherm.hermchat.tools.search

/**
 * 从搜索摘要文本中提取可打开的 http(s) URL。
 */
object SearchResultUrls {
    private val URL = Regex(
        """https?://[^\s<>"'）】\]\|]+""",
        RegexOption.IGNORE_CASE,
    )

    fun firstHttpUrl(text: String): String? {
        val match = URL.find(text) ?: return null
        return match.value.trimEnd('.', ',', ';', '。', '，', ')', '）', ']', '》')
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    fun allHttpUrls(text: String, limit: Int = 8): List<String> {
        val seen = LinkedHashSet<String>()
        for (m in URL.findAll(text)) {
            val u = m.value.trimEnd('.', ',', ';', '。', '，', ')', '）', ']', '》')
            if (u.startsWith("http://") || u.startsWith("https://")) {
                seen.add(u)
                if (seen.size >= limit) break
            }
        }
        return seen.toList()
    }
}
