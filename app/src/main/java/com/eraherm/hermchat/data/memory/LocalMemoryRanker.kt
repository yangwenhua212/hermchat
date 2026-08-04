package com.eraherm.hermchat.data.memory

/**
 * 极简本地召回：关键词 / 子串重叠，无向量。
 * 钉死记忆加权；同分离时更靠前（由调用方按 updatedAt 预排序亦可）。
 */
object LocalMemoryRanker {
    fun tokens(text: String): Set<String> {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return emptySet()
        val out = linkedSetOf<String>()
        // 连续 CJK 切成单字 + 二字；拉丁/数字按词
        val cjk = StringBuilder()
        val latin = StringBuilder()
        fun flushCjk() {
            if (cjk.isEmpty()) return
            val s = cjk.toString()
            cjk.clear()
            if (s.length == 1) {
                out += s
            } else {
                s.forEach { out += it.toString() }
                for (i in 0 until s.length - 1) {
                    out += s.substring(i, i + 2)
                }
            }
        }
        fun flushLatin() {
            if (latin.isEmpty()) return
            val w = latin.toString()
            latin.clear()
            if (w.length >= 2) out += w
        }
        for (ch in t) {
            when {
                ch.isIdeograph() -> {
                    flushLatin()
                    cjk.append(ch)
                }
                ch.isLetterOrDigit() -> {
                    flushCjk()
                    latin.append(ch)
                }
                else -> {
                    flushCjk()
                    flushLatin()
                }
            }
        }
        flushCjk()
        flushLatin()
        return out
    }

    fun score(query: String, content: String, pinned: Boolean): Double {
        val q = query.trim()
        val c = content.trim()
        if (q.isEmpty() || c.isEmpty()) return 0.0
        var s = 0.0
        if (c.contains(q, ignoreCase = true) || q.contains(c, ignoreCase = true)) {
            s += 5.0
        }
        val qt = tokens(q)
        val ct = tokens(c)
        if (qt.isNotEmpty() && ct.isNotEmpty()) {
            val hit = qt.count { it in ct }
            s += hit * 1.5
            s += (hit.toDouble() / qt.size) * 2.0
        }
        if (pinned) s += 1.5
        return s
    }

    fun <T> rank(
        query: String,
        items: List<T>,
        topK: Int,
        contentOf: (T) -> String,
        pinnedOf: (T) -> Boolean,
        updatedAtOf: (T) -> Long = { 0L },
    ): List<Scored<T>> {
        val k = topK.coerceIn(1, 50)
        return items.mapNotNull { item ->
            val sc = score(query, contentOf(item), pinnedOf(item))
            if (sc <= 0.0) null else Scored(item, sc)
        }
            .sortedWith(
                compareByDescending<Scored<T>> { it.score }
                    .thenByDescending { pinnedOf(it.item) }
                    .thenByDescending { updatedAtOf(it.item) },
            )
            .take(k)
    }

    data class Scored<T>(val item: T, val score: Double)

    private fun Char.isIdeograph(): Boolean =
        this in '\u4e00'..'\u9fff' || this in '\u3400'..'\u4dbf'
}
