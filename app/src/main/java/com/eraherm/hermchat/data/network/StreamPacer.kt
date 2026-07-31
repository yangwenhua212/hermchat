package com.eraherm.hermchat.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Pace streamed tokens for on-screen typing feel (closer to Doubao / DeepSeek).
 * Network may deliver whole words at once; we re-emit per character.
 */
fun Flow<String>.paceForDisplay(charDelayMs: Long = 28L): Flow<String> = flow {
    collect { chunk ->
        if (chunk.isEmpty()) return@collect
        if (chunk.length == 1) {
            emit(chunk)
            delay(charDelayMs)
            return@collect
        }
        for (ch in chunk) {
            emit(ch.toString())
            delay(charDelayMs)
        }
    }
}
