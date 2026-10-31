package com.faiza.ai

import com.faiza.ai.ai.FaizaAgent
import com.faiza.ai.ai.provider.Attachment
import com.faiza.ai.ai.provider.ChatTurn
import com.faiza.ai.util.AttachmentReader
import com.faiza.ai.util.AttachmentReader.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chat attachments (video 30:51): what can be read, and old files aren't re-sent forever. */
class ChatAttachmentTest {

    @Test fun recognisesFileKinds() {
        assertEquals(Kind.IMAGE, AttachmentReader.kindOf("image/heic", "IMG_1.heic"))
        assertEquals(Kind.IMAGE, AttachmentReader.kindOf(null, "photo.JPG"))
        assertEquals(Kind.PDF, AttachmentReader.kindOf("application/pdf", "notes"))
        assertEquals(Kind.PDF, AttachmentReader.kindOf("application/octet-stream", "Bill.PDF"))
        assertEquals(Kind.TEXT, AttachmentReader.kindOf("text/plain", "a.txt"))
        assertEquals(Kind.TEXT, AttachmentReader.kindOf(null, "data.csv"))
        assertEquals(Kind.TEXT, AttachmentReader.kindOf("application/json", "x"))
        assertEquals(Kind.UNSUPPORTED, AttachmentReader.kindOf("application/zip", "a.zip"))
        assertEquals(Kind.UNSUPPORTED, AttachmentReader.kindOf("video/mp4", "clip.mp4"))
    }

    private fun photo(n: Int) = Attachment("p$n.jpg", "image/jpeg", base64 = "AAAA")

    @Test fun onlyTheLatestAttachmentsAreResent() {
        val history = listOf(
            ChatTurn.User("first", files = listOf(photo(1))),
            ChatTurn.Assistant("a cat"),
            ChatTurn.User("second", files = listOf(photo(2))),
            ChatTurn.Assistant("a dog"),
            ChatTurn.User("no file"),
            ChatTurn.Assistant("ok"),
            ChatTurn.User("third", files = listOf(photo(3)))
        )
        val out = FaizaAgent.stripOldAttachments(history, keep = 2)
        assertEquals(history.size, out.size)
        val first = out[0] as ChatTurn.User
        assertTrue(first.files.isEmpty())
        assertEquals("first [attached earlier: p1.jpg]", first.text)
        assertEquals(1, (out[2] as ChatTurn.User).files.size)
        assertEquals(1, (out[6] as ChatTurn.User).files.size)
        assertEquals("no file", (out[4] as ChatTurn.User).text)
    }
}
