package com.eraherm.hermchat.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

/**
 * 把网络 / 系统异常转成用户能看懂的中文短句。
 * 已是中文的业务错误原样保留（截断过长内容）。
 */
object UserFacingError {
    fun of(err: Throwable?, fallback: String = "出了点问题，请稍后再试"): String {
        if (err == null) return fallback
        if (err is CancellationException) return "已取消"
        if (err is com.eraherm.hermchat.data.network.FirstChunkTimeoutException) {
            return "云端响应超时"
        }

        val msg = err.message?.trim().orEmpty()
        // 业务侧已写好的中文提示
        if (looksChinese(msg) && !looksTechnicalEnglish(msg)) {
            return msg.take(80)
        }

        return when (err) {
            is UnknownHostException -> "找不到服务器，请检查网络或地址"
            is ConnectException -> "连不上服务器，请确认地址、端口和同一 Wi‑Fi"
            is SocketTimeoutException -> "连接超时，请检查网络后重试"
            is SSLException -> "安全连接失败，请检查是否用了 https / 证书"
            else -> mapMessage(msg, err, fallback)
        }
    }

    fun ofMessage(raw: String?, fallback: String = "出了点问题，请稍后再试"): String =
        of(RuntimeException(raw), fallback)

    private fun mapMessage(msg: String, err: Throwable, fallback: String): String {
        val lower = msg.lowercase()
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ||
                lower.contains("unknownhost") ||
                lower.contains("no address associated") ->
                "找不到服务器，请检查网络或地址"

            lower.contains("failed to connect") ||
                lower.contains("connection refused") ||
                lower.contains("econnrefused") ||
                lower.contains("network is unreachable") ->
                "连不上服务器，请确认地址、端口和同一 Wi‑Fi"

            lower.contains("timeout") || lower.contains("timed out") ->
                "连接超时，请检查网络后重试"

            lower.contains("cleartext") || lower.contains("cleartext communication") ->
                "明文 HTTP 被系统拦截，请改用 https 或检查设置"

            lower.contains("certificate") || lower.contains("ssl") || lower.contains("handshake") ->
                "安全连接失败，请检查证书或改用 https"

            lower.contains("unexpected end of stream") || lower.contains("connection reset") ->
                "连接被中断，请再试一次"

            lower.contains("software caused connection abort") ->
                "连接已中断，请再试一次"

            Regex("""\bHTTP\s*401\b""", RegexOption.IGNORE_CASE).containsMatchIn(msg) ||
                lower.contains("unauthorized") ->
                "未授权，请检查 API Key 或登录令牌"

            Regex("""\bHTTP\s*403\b""", RegexOption.IGNORE_CASE).containsMatchIn(msg) ||
                lower.contains("forbidden") ->
                "没有权限，请检查密钥或模型许可"

            Regex("""\bHTTP\s*404\b""", RegexOption.IGNORE_CASE).containsMatchIn(msg) ->
                "地址不存在，请检查接口路径"

            Regex("""\bHTTP\s*429\b""", RegexOption.IGNORE_CASE).containsMatchIn(msg) ->
                "请求太频繁，请稍后再试"

            Regex("""\bHTTP\s*5\d\d\b""", RegexOption.IGNORE_CASE).containsMatchIn(msg) ->
                "服务器出错，请稍后再试"

            msg.startsWith("HTTP ") && looksChinese(msg.substringAfter(':').trim()) ->
                msg.substringAfter(':').trim().ifBlank { fallback }.take(80)

            msg.contains("服务可达") ||
                msg.contains("路径不存在") ||
                msg.contains("请求被拒绝") ||
                msg.contains("模型名") ||
                msg.contains("下载失败") ||
                msg.contains("需要 Hugging Face") ||
                msg.contains("令牌") ||
                msg.contains("已暂停") ->
                msg.take(80)

            msg.isBlank() -> fallback
            looksTechnicalEnglish(msg) -> fallback
            else -> msg.take(80)
        }.ifBlank { fallback }
    }

    private fun looksChinese(s: String): Boolean =
        s.any { it in '\u4e00'..'\u9fff' }

    private fun looksTechnicalEnglish(s: String): Boolean {
        if (s.isBlank()) return false
        if (looksChinese(s)) return false
        val lower = s.lowercase()
        return lower.contains("exception") ||
            lower.contains("unable to") ||
            lower.contains("failed to") ||
            lower.contains("error:") ||
            lower.contains("java.") ||
            lower.contains("android.") ||
            lower.contains("unknownhost") ||
            lower.startsWith("http ") ||
            Regex("""\b[A-Z][a-zA-Z]+Exception\b""").containsMatchIn(s)
    }
}
