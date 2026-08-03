package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.local.GatewayRouteMode

/**
 * ④ 端侧网关路由：
 * - [GatewayRouteMode.AUTO]：默认偏向云脑（更聪明）；仅极短闲聊走本地
 * - [GatewayRouteMode.LOCAL] / [GatewayRouteMode.API]：用户手选优先通道
 */
object GatewayRouter {
    enum class Route { LOCAL, API }

    fun decide(
        prompt: String,
        localReady: Boolean,
        apiConfigured: Boolean,
        mode: GatewayRouteMode = GatewayRouteMode.AUTO,
    ): Route {
        when (mode) {
            GatewayRouteMode.LOCAL -> {
                if (localReady) return Route.LOCAL
                if (apiConfigured) return Route.API
                return Route.LOCAL
            }
            GatewayRouteMode.API -> {
                if (apiConfigured) return Route.API
                if (localReady) return Route.LOCAL
                return Route.API
            }
            GatewayRouteMode.AUTO -> Unit
        }

        if (apiConfigured && !localReady) return Route.API
        if (!apiConfigured && localReady) return Route.LOCAL
        if (!apiConfigured) return Route.API

        val p = prompt.trim()
        if (p.isEmpty()) return Route.LOCAL

        // 任务 / 工具 / 推理 → 一律云脑
        if (FORCE_API.any { p.contains(it, ignoreCase = true) }) return Route.API
        if (p.length >= LONG_CHARS) return Route.API
        if (COMPLEX.any { p.contains(it, ignoreCase = true) }) return Route.API
        if (TASK.any { p.contains(it, ignoreCase = true) }) return Route.API

        // 仅极短寒暄走本地省钱
        if (localReady && (p.length <= SHORT_CHARS || SIMPLE.any { p.equals(it, true) || p.startsWith(it) })) {
            return Route.LOCAL
        }
        // 其余默认 API（④ 以聪明为先）
        return if (apiConfigured) Route.API else Route.LOCAL
    }

    fun isWeakLocalReply(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        return WEAK_MARKERS.any { t.contains(it) }
    }

    private const val SHORT_CHARS = 16
    private const val LONG_CHARS = 48

    private val FORCE_API = listOf(
        "用大模型", "用 api", "用API", "deepseek", "gpt", "详细分析", "写一篇", "写代码",
        "refactor", "implement",
    )

    private val COMPLEX = listOf(
        "分析一下", "帮我写", "翻译这篇", "总结这篇", "代码", "方案设计",
        "对比一下", "为什么会", "怎么实现", "步骤详细", "长文", "规划", "安排一下",
        "怎么办", "如何", "解释", "评价", "润色", "改写",
    )

    private val TASK = listOf(
        "提醒", "闹钟", "倒计时", "日程", "日历", "开会", "预约",
        "搜索", "查一下", "打开", "分享", "发给", "链接", "网址",
        "半小时", "明天", "后天", "下周", "分钟后", "小时后",
        "帮我", "麻烦", "记得", "别忘",
    )

    private val SIMPLE = listOf(
        "你好", "在吗", "嗨", "hello", "hi", "谢谢", "早安", "晚安", "嗯", "好的",
    )

    private val WEAK_MARKERS = listOf(
        "本地模型未就绪",
        "本地推理暂不可用",
        "下载本地模型后",
        "该设备内存不足",
        "已收到。下载本地模型",
    )
}
