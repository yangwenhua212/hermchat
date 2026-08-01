package com.eraherm.hermchat.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 流式展示：按网络到达的整段 token 直接吐出（接近豆包 / DeepSeek），
 * 不再拆成逐字 delay，避免聊天发闷、跟不上。
 */
fun Flow<String>.paceForDisplay(charDelayMs: Long = 0L): Flow<String> = flow {
    collect { chunk ->
        if (chunk.isEmpty()) return@collect
        emit(chunk)
        if (charDelayMs > 0L) {
            kotlinx.coroutines.delay(charDelayMs)
        }
    }
}
