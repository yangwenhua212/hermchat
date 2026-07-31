package com.eraherm.hermchat.data.network

import android.content.Context
import android.net.wifi.WifiManager
import com.eraherm.hermchat.data.model.AgentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class ProbeHit(
    val endpoint: String,
    val detail: String,
)

/**
 * 探测模拟器本机映射与当前 Wi‑Fi 网关上的常见 Agent 端口。
 */
class EndpointProbe(
    context: Context,
    private val tester: ConnectionTester = ConnectionTester(
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build(),
    ),
) {
    private val appContext = context.applicationContext

    suspend fun discover(kind: AgentKind): List<ProbeHit> = withContext(Dispatchers.IO) {
        val urls = candidates(kind).distinct()
        coroutineScope {
            urls.map { url ->
                async {
                    tester.test(url).getOrNull()?.let { detail ->
                        ProbeHit(endpoint = url, detail = detail)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    fun candidates(kind: AgentKind): List<String> {
        val hosts = buildList {
            add("10.0.2.2") // 模拟器 → 宿主机
            gatewayHost()?.let { add(it) }
        }.distinct()

        return when (kind) {
            AgentKind.WEBSOCKET -> hosts.flatMap { host ->
                listOf(
                    "ws://$host:8765/ws",
                    "ws://$host:8765/api/ws",
                    "ws://$host:18789/ws",
                    "ws://$host:8080/ws",
                    "ws://$host:3000/ws",
                )
            }
            AgentKind.HTTP_COMPAT -> hosts.flatMap { host ->
                listOf(
                    "http://$host:5000",
                    "http://$host:8000",
                    "http://$host:11434",
                    "http://$host:3000",
                    "http://$host:8080",
                )
            }
            AgentKind.CUSTOM -> (
                candidates(AgentKind.WEBSOCKET) + candidates(AgentKind.HTTP_COMPAT)
                ).distinct()

            AgentKind.LOCAL -> emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun gatewayHost(): String? {
        return runCatching {
            val wifi = appContext.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val gateway = wifi.dhcpInfo?.gateway ?: return null
            if (gateway == 0) return null
            formatIp(gateway)
        }.getOrNull()
    }

    /** dhcpInfo 网关为小端 int。 */
    private fun formatIp(value: Int): String {
        return listOf(
            value and 0xff,
            value shr 8 and 0xff,
            value shr 16 and 0xff,
            value shr 24 and 0xff,
        ).joinToString(".")
    }
}
