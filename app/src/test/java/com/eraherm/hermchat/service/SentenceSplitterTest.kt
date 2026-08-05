package com.eraherm.hermchat.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {
    @Test
    fun pullsCompleteSentence() {
        val (parts, cursor) = SentenceSplitter.takeNew("你好世界，今天不错。还有半句", 0, minChars = 4)
        assertEquals(listOf("你好世界，今天不错。"), parts)
        assertTrue(cursor > 0)
    }

    @Test
    fun flushTail() {
        val (parts, cursor) = SentenceSplitter.takeNew("尾巴没标点", 0, forceFlush = true, minChars = 8)
        assertEquals(listOf("尾巴没标点"), parts)
        assertEquals("尾巴没标点".length, cursor)
    }

    @Test
    fun waitsForMinChars() {
        val (parts, _) = SentenceSplitter.takeNew("短。", 0, minChars = 8)
        assertTrue(parts.isEmpty())
    }

    @Test
    fun longWithoutPunctuation() {
        val long = "甲".repeat(60)
        val (parts, cursor) = SentenceSplitter.takeNew(long, 0)
        assertEquals(1, parts.size)
        assertTrue(cursor >= 48)
    }
}
