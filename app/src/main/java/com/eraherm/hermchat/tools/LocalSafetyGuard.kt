package com.eraherm.hermchat.tools

/**
 * Agent loop 安全门：明显违法/犯罪协助请求 → 拒绝工具规划，并给出简短拒绝说明。
 * 不能替代模型判断，只拦截高置信口语意图；灰色/玩笑边界交给 Prompt。
 */
object LocalSafetyGuard {
    data class Refusal(
        /** 给用户看的完整回复 */
        val userMessage: String,
        /** 命中的类别短名（调试/测试用） */
        val category: String,
    )

    /**
     * 若应拒绝则返回说明；否则 null。
     * 对「如何防诈骗 / 报警 / 法律概念」等防卫性、知情性提问不拦截。
     */
    fun refusalIfNeeded(userText: String): Refusal? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        if (isDefensiveOrInquiry(text)) return null
        for ((category, patterns) in BLOCK_RULES) {
            if (patterns.any { it.containsMatchIn(text) }) {
                return Refusal(
                    userMessage = buildRefusal(category),
                    category = category,
                )
            }
        }
        return null
    }

    fun shouldBlockTools(userText: String): Boolean =
        refusalIfNeeded(userText) != null

    private fun isDefensiveOrInquiry(text: String): Boolean {
        val defensive = listOf(
            "防诈骗", "防骗", "如何防范", "怎么识破", "报警", "急救",
            "法律上", "违法吗", "是否违法", "算不算犯罪", "科普",
            "新闻里", "电影里", "小说里", "虚构",
        )
        return defensive.any { text.contains(it) }
    }

    private fun buildRefusal(category: String): String {
        val why = CATEGORY_WHY[category] ?: "涉及违法或严重伤害他人的行为"
        return "这个我帮不了。$why，我不能协助规划或执行。若你遇到相关困扰，请联系家人、朋友或当地报警/求助热线。"
    }

    private val CATEGORY_WHY = mapOf(
        "暴力伤害" to "涉及伤害他人或制造危险物品",
        "诈骗盗号" to "涉及诈骗、盗号或未经授权获取他人账号",
        "入侵攻击" to "涉及未经授权入侵系统、传播恶意软件",
        "违禁制毒" to "涉及制毒、非法交易违禁品等",
        "证件造假" to "涉及伪造证件、假币或身份欺诈",
        "儿童侵害" to "涉及侵害未成年人",
        "恐怖极端" to "涉及恐怖活动或极端暴力宣传",
    )

    /**
     * 高置信「动手/教我做」类；避免过宽误伤。
     */
    private val BLOCK_RULES: List<Pair<String, List<Regex>>> = listOf(
        "儿童侵害" to listOf(
            Regex("""(儿童|幼童|未成年).{0,12}(色情|性剥削|性侵)"""),
            Regex("""(制作|传播).{0,8}(儿童色情|幼女)"""),
        ),
        "恐怖极端" to listOf(
            Regex("""(如何|怎么|教我|帮我).{0,10}(制造|制作).{0,6}(炸弹|炸药|爆炸物)"""),
            Regex("""(策划|实施).{0,8}(恐袭|恐怖袭击)"""),
        ),
        "暴力伤害" to listOf(
            Regex("""(如何|怎么|教我|帮我).{0,10}(杀人|谋杀|下毒|毒死)"""),
            Regex("""(雇凶|买凶|暗杀).{0,8}(方法|怎么|教程)"""),
            Regex("""(制作|配制).{0,8}(毒药|毒饵).{0,6}(害人|杀人)?"""),
        ),
        "诈骗盗号" to listOf(
            Regex("""(如何|怎么|教我|帮我).{0,12}(诈骗|骗钱|电信诈骗|钓鱼网站)"""),
            Regex("""(如何|怎么|教我|帮我).{0,12}(盗号|撞库|窃取).{0,8}(密码|账号|验证码)"""),
            Regex("""(帮我|替我).{0,8}(骗|诈).{0,6}(钱|转账)"""),
        ),
        "入侵攻击" to listOf(
            Regex("""(如何|怎么|教我|帮我).{0,12}(入侵|黑进|攻击).{0,10}(服务器|网站|系统|电脑)"""),
            Regex("""(编写|制作|传播).{0,8}(勒索软件|木马|病毒).{0,6}(攻击|感染)?"""),
            Regex("""(绕过|破解).{0,8}(支付|银行).{0,6}(安全|验证)"""),
        ),
        "违禁制毒" to listOf(
            Regex("""(如何|怎么|教我|帮我).{0,10}(合成|制造|制作).{0,8}(冰毒|海洛因|芬太尼|毒品)"""),
            Regex("""(购买|贩卖).{0,6}(冰毒|海洛因|枪支).{0,6}(渠道|途径|联系)"""),
        ),
        "证件造假" to listOf(
            Regex("""(如何|怎么|教我|帮我).{0,10}(伪造|假造).{0,8}(身份证|护照|文凭|发票|货币|假币)"""),
            Regex("""(办假证|假身份证|假护照).{0,8}(怎么|哪里|教程)"""),
        ),
    )
}
