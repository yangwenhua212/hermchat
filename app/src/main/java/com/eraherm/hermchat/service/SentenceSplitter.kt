package com.eraherm.hermchat.service

/**
 * 从流式全文中切出「已完整」的句子，供边生成边朗读。
 */
object SentenceSplitter {
    private val END_CHARS = charArrayOf('。', '！', '？', '；', '!', '?', ';', '\n', '…')

    /**
     * @param fullText 当前已生成全文（原始助手文本）
     * @param fromIndex 已送去朗读的字符游标
     * @param forceFlush 流结束时把剩余尾巴也读掉
     * @param minChars 最短一句，避免一字一读
     * @return 新句子列表 + 新游标
     */
    fun takeNew(
        fullText: String,
        fromIndex: Int,
        forceFlush: Boolean = false,
        minChars: Int = 8,
    ): Pair<List<String>, Int> {
        if (fromIndex >= fullText.length) {
            return emptyList<String>() to fromIndex
        }
        val out = ArrayList<String>()
        var cursor = fromIndex
        var i = fromIndex
        while (i < fullText.length) {
            val ch = fullText[i]
            if (ch in END_CHARS) {
                val end = i + 1
                val chunk = fullText.substring(cursor, end).trim()
                if (chunk.length >= minChars || (chunk.isNotEmpty() && forceFlush)) {
                    if (chunk.isNotEmpty()) out.add(chunk)
                    cursor = end
                } else if (chunk.isNotEmpty() && out.isNotEmpty()) {
                    // 太短：并到上一句语义上已切完，继续等；此处仅推进标点后空白
                    cursor = end
                }
                i = end
                continue
            }
            i++
        }
        if (forceFlush && cursor < fullText.length) {
            val tail = fullText.substring(cursor).trim()
            if (tail.isNotEmpty()) {
                out.add(tail)
                cursor = fullText.length
            }
        }
        // 长句无标点：满 48 字也切一刀，降低首包等待
        if (!forceFlush && out.isEmpty() && fullText.length - fromIndex >= 48) {
            val slice = fullText.substring(fromIndex, fromIndex + 48)
            val breakAt = slice.indexOfLast { it == '，' || it == ',' || it == ' ' }
                .takeIf { it >= 12 }
                ?: 48
            val chunk = fullText.substring(fromIndex, fromIndex + breakAt).trim()
            if (chunk.isNotEmpty()) {
                out.add(chunk)
                cursor = fromIndex + breakAt
            }
        }
        return out to cursor
    }
}
