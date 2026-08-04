package com.eraherm.hermchat.viewmodel

/**
 * ④ Agent loop 中间态：每步上屏，避免黑盒等待像死机。
 * 文案保持一行内短句（见 UI.md）；阶段前缀让用户感到「分析→执行→观察」。
 */
sealed class LoopStep {
    data object Idle : LoopStep()

    data class Planning(val thought: String = "") : LoopStep()

    data class Executing(
        val toolName: String,
        val desc: String = "",
    ) : LoopStep()

    data class Observing(val result: String) : LoopStep()

    data class Finished(val answer: String = "") : LoopStep()

    data class Error(val msg: String) : LoopStep()

    /** 聊天侧展示用短句；Idle 为 null。 */
    fun label(): String? = when (this) {
        Idle -> null
        is Planning -> {
            val t = thought.trim()
            when {
                t.isBlank() || t == "正在分析…" -> "分析中…"
                t.startsWith("分析") || t.startsWith("第") ||
                    t.startsWith("改用") || t.startsWith("纠正") ||
                    t.startsWith("本地") -> t.take(40)
                else -> "分析中 · ${t.take(28)}"
            }
        }
        is Executing -> {
            val what = desc.ifBlank { friendlyToolName(toolName) }
            "执行中 · ${what.take(28)}"
        }
        is Observing -> {
            val r = result.trim()
            if (r.isBlank()) "观察中…" else "观察中 · ${r.take(28)}"
        }
        is Finished -> answer.trim().take(40).ifBlank { "已完成" }
        is Error -> msg.trim().take(48)
    }

    companion object {
        fun friendlyToolName(name: String): String = when (name) {
            "alarm.create" -> "设置提醒"
            "calendar.create" -> "添加日历"
            "url.open" -> "打开链接"
            "web.search" -> "搜索"
            "share.text" -> "分享"
            "clipboard.read" -> "读剪贴板"
            "clipboard.write" -> "写剪贴板"
            "app.open" -> "打开应用"
            "phone.dial" -> "拨号"
            "memory.recall" -> "召回记忆"
            "memory.remember" -> "写入记忆"
            "maps.search" -> "打开地图"
            "email.compose" -> "写邮件"
            else -> name
        }
    }
}
