package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.local.GatewayRouteMode

/**
 * ④ 端侧网关路由：
 * - [GatewayRouteMode.API] / 旧 [GatewayRouteMode.AUTO]：默认云端；未配 API 才回落本地
 * - [GatewayRouteMode.LOCAL]：用户确认风险后手选本地；本地未就绪才回落 API
 */
object GatewayRouter {
    enum class Route { LOCAL, API }

    fun decide(
        prompt: String,
        localReady: Boolean,
        apiConfigured: Boolean,
        mode: GatewayRouteMode = GatewayRouteMode.API,
    ): Route {
        val effective = if (mode == GatewayRouteMode.AUTO) GatewayRouteMode.API else mode
        return when (effective) {
            GatewayRouteMode.LOCAL -> when {
                localReady -> Route.LOCAL
                apiConfigured -> Route.API
                else -> Route.LOCAL
            }
            GatewayRouteMode.API, GatewayRouteMode.AUTO -> when {
                apiConfigured -> Route.API
                localReady -> Route.LOCAL
                else -> Route.API
            }
        }
    }

    fun isWeakLocalReply(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        return WEAK_MARKERS.any { t.contains(it) }
    }

    private val WEAK_MARKERS = listOf(
        "本地模型未就绪",
        "本地推理暂不可用",
        "下载本地模型后",
        "该设备内存不足",
        "已收到。下载本地模型",
    )
}
