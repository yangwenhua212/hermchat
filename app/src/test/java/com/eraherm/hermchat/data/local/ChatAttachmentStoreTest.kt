package com.eraherm.hermchat.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAttachmentStoreTest {
    @Test
    fun textMimeByTypeAndExt() {
        assertTrue(ChatAttachmentStore.isTextMime("text/plain"))
        assertTrue(ChatAttachmentStore.isTextMime("application/json"))
        assertTrue(ChatAttachmentStore.isTextMime("", "notes.md"))
        assertFalse(ChatAttachmentStore.isTextMime("application/pdf", "a.pdf"))
        assertTrue(ChatAttachmentStore.isImageMime("image/jpeg"))
    }
}
