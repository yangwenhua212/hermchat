package com.eraherm.hermchat.service

/**
 * Maps UI wake phrases to sherpa-onnx Chinese KWS keyword lines.
 * Format: space-separated pinyin letters + `@显示名` (see wenetspeech keywords.txt).
 */
object SherpaKeywordCodec {

    fun toKeywordLine(phrase: String): String {
        val trimmed = phrase.trim()
        return PHRASE_TO_KEYWORD[trimmed] ?: buildFallback(trimmed)
    }

    private fun buildFallback(phrase: String): String {
        val letters = phrase.mapNotNull { CHAR_PINYIN[it] }
            .joinToString(" ") { it }
        return if (letters.isBlank()) {
            DEFAULT_KEYWORD
        } else {
            "$letters @$phrase"
        }
    }

    private val PHRASE_TO_KEYWORD = mapOf(
        "小助手" to "x iǎo z hù s hǒu @小助手",
        "小黑" to "x iǎo h ēi @小黑",
        "嘿助手" to "h ēi z hù s hǒu @嘿助手",
        // English letters are weak on the Chinese KWS model; alias to 小助手.
        "HxSync" to "x iǎo z hù s hǒu @HxSync",
    )

    private val CHAR_PINYIN = mapOf(
        '小' to "x iǎo",
        '助' to "z hù",
        '手' to "s hǒu",
        '黑' to "h ēi",
        '嘿' to "h ēi",
        '同' to "t óng",
        '学' to "x ué",
        '你' to "n ǐ",
        '好' to "h ǎo",
    )

    const val DEFAULT_KEYWORD = "x iǎo z hù s hǒu @小助手"
}
