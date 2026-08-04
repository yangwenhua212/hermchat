package com.eraherm.hermchat.viewmodel

/**
 * ④ Agent loop 中间态：每步上屏，避免黑盒等待像死机。
 * 文案保持一行内短句（见 UI.md）。
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
        is Planning -> thought.ifBlank { "正在分析…" }
        is Executing -> {
            val what = desc.ifBlank { friendlyToolName(toolName) }
            "正在执行：$what"
        }
        is Observing -> "已完成：${result.trim().take(40)}"
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
            else -> name
        }
    }
}
