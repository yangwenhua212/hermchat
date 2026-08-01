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
        return hosts.flatMap { host -> candidatesForHost(host, kind) }.distinct()
    }

    fun candidatesForHost(host: String, kind: AgentKind): List<String> {
        val h = host.trim().removePrefix("http://").removePrefix("https://")
            .removePrefix("ws://").removePrefix("wss://")
            .substringBefore("/")
            .trimEnd('/')
        if (h.isEmpty()) return emptyList()
        return when (kind) {
            AgentKind.WEBSOCKET -> listOf(
                "ws://$h:8765/ws",
                "ws://$h:8765/api/ws",
                "ws://$h:18789/ws",
                "ws://$h:8080/ws",
                "ws://$h:3000/ws",
                "ws://$h/ws",
                "ws://$h/api/ws",
            )
            AgentKind.HERMES -> listOf(
                "http://$h",
                "http://$h:80",
                "http://$h:5000",
                "http://$h:8000",
                "http://$h:8080",
                "http://$h:3000",
            )
            AgentKind.HTTP_COMPAT -> listOf(
                "http://$h:5000",
                "http://$h:8000",
                "http://$h:11434",
                "http://$h:3000",
                "http://$h:8080",
                "http://$h",
            )
            AgentKind.GATEWAY -> candidatesForHost(h, AgentKind.HTTP_COMPAT)
            AgentKind.CUSTOM -> (
                candidatesForHost(h, AgentKind.WEBSOCKET) +
                    candidatesForHost(h, AgentKind.HERMES)
                ).distinct()
            AgentKind.LOCAL -> emptyList()
        }
    }

    suspend fun discoverOnHost(host: String, kind: AgentKind): List<ProbeHit> =
        withContext(Dispatchers.IO) {
            val urls = candidatesForHost(host, kind).distinct()
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
