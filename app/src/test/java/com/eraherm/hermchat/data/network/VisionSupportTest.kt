package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionSupportTest {
    @Test
    fun httpAndHermesSupportImage() {
        assertTrue(VisionSupport.canSendImage(profile(AgentKind.HTTP_COMPAT)))
        assertTrue(VisionSupport.canSendImage(profile(AgentKind.HERMES)))
    }

    @Test
    fun gatewayNeedsHttpEndpoint() {
        assertTrue(
            VisionSupport.canSendImage(
                profile(AgentKind.GATEWAY, endpoint = "https://api.deepseek.com"),
            ),
        )
        assertFalse(
            VisionSupport.canSendImage(
                profile(AgentKind.GATEWAY, endpoint = ""),
            ),
        )
    }

    @Test
    fun localAndWsRejected() {
        assertFalse(VisionSupport.canSendImage(profile(AgentKind.LOCAL)))
        assertFalse(VisionSupport.canSendImage(profile(AgentKind.WEBSOCKET)))
    }

    private fun profile(
        kind: AgentKind,
        endpoint: String = kind.defaultEndpoint,
    ) = AgentProfile(
        id = "t",
        kind = kind,
        name = "t",
        endpoint = endpoint,
    )
}
