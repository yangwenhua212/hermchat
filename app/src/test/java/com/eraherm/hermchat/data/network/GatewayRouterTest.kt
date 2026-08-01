package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.local.GatewayRouteMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRouterTest {
    @Test
    fun simpleGoesLocalWhenReady() {
        val r = GatewayRouter.decide("你好", localReady = true, apiConfigured = true)
        assertEquals(GatewayRouter.Route.LOCAL, r)
    }

    @Test
    fun complexGoesApi() {
        val r = GatewayRouter.decide(
            "帮我写一个排序算法并详细分析复杂度",
            localReady = true,
            apiConfigured = true,
        )
        assertEquals(GatewayRouter.Route.API, r)
    }

    @Test
    fun noLocalFallsBackApi() {
        val r = GatewayRouter.decide("你好", localReady = false, apiConfigured = true)
        assertEquals(GatewayRouter.Route.API, r)
    }

    @Test
    fun forceApiKeyword() {
        val r = GatewayRouter.decide("用大模型解释相对论", localReady = true, apiConfigured = true)
        assertEquals(GatewayRouter.Route.API, r)
    }

    @Test
    fun manualLocalIgnoresComplexity() {
        val r = GatewayRouter.decide(
            "帮我写一个排序算法并详细分析复杂度",
            localReady = true,
            apiConfigured = true,
            mode = GatewayRouteMode.LOCAL,
        )
        assertEquals(GatewayRouter.Route.LOCAL, r)
    }

    @Test
    fun manualApiIgnoresSimple() {
        val r = GatewayRouter.decide(
            "你好",
            localReady = true,
            apiConfigured = true,
            mode = GatewayRouteMode.API,
        )
        assertEquals(GatewayRouter.Route.API, r)
    }

    @Test
    fun manualLocalFallsBackWhenNotReady() {
        val r = GatewayRouter.decide(
            "你好",
            localReady = false,
            apiConfigured = true,
            mode = GatewayRouteMode.LOCAL,
        )
        assertEquals(GatewayRouter.Route.API, r)
    }

    @Test
    fun weakLocalReplyDetected() {
        assertTrue(GatewayRouter.isWeakLocalReply("本地模型未就绪。请在配置里下载"))
        assertFalse(GatewayRouter.isWeakLocalReply("今天天气不错，适合出门。"))
    }
}
