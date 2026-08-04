package com.eraherm.hermchat.data.network

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

/** 流式首包（第一段非空 token）超时。 */
class FirstChunkTimeoutException(
    val timeoutMs: Long,
) : Exception("云端响应超时")

/**
 * 在 [timeoutMs] 内必须收到至少一段内容，否则取消并抛 [FirstChunkTimeoutException]。
 * 首包到达后不再限制后续 chunk（长回复不受影响）。
 * 流正常结束且从未吐字：不抛超时（空回复）。
 */
suspend fun Flow<String>.collectWithFirstChunkTimeout(
    timeoutMs: Long,
    onEach: suspend (String) -> Unit,
) {
    coroutineScope {
        var gotFirst = false
        val job = launch {
            collect { piece ->
                if (piece.isNotEmpty()) gotFirst = true
                onEach(piece)
            }
        }
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(1L)
        while (isActive && !gotFirst && job.isActive) {
            if (System.currentTimeMillis() >= deadline) {
                job.cancel()
                throw FirstChunkTimeoutException(timeoutMs)
            }
            delay(40)
        }
        job.join()
    }
}

object StreamFirstChunk {
    /** ④ / HTTP / WS 首包等待上限 */
    const val TIMEOUT_MS = 12_000L
}
