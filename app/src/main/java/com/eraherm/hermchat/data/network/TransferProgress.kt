package com.eraherm.hermchat.data.network

/** 通用下载进度（本地模型 / 唤醒 ASR 等）。 */
data class TransferProgress(
    val label: String,
    val bytesRead: Long,
    val totalBytes: Long,
    val bytesPerSec: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    fun statusLine(): String {
        val pct = if (totalBytes > 0L) "${(fraction * 100).toInt()}%" else "…"
        val size = if (totalBytes > 0L) {
            "${formatBytes(bytesRead)}/${formatBytes(totalBytes)}"
        } else {
            formatBytes(bytesRead)
        }
        val spd = if (bytesPerSec > 0L) " · ${formatBytes(bytesPerSec)}/s" else ""
        return "下载 $label $pct · $size$spd"
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "${bytes}B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.0fKB", kb)
            val mb = kb / 1024.0
            return if (mb < 100) String.format("%.1fMB", mb) else String.format("%.0fMB", mb)
        }
    }
}
