package de.heckenmann.visualagent.agent.codexdirect

import com.openai.core.jsonMapper
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeType
import kotlin.test.assertEquals

class DirectPreparedAttachmentTest {
    @Test
    fun `counting and repeated inference reuse prepared bytes without changing the wire content`() {
        listOf("text/plain", "application/pdf", "image/png").forEach { mime ->
            val original =
                Media
                    .builder()
                    .mimeType(MimeType.valueOf(mime))
                    .name("attachment")
                    .data(ByteArrayResource(byteArrayOf(1, 2, 3)))
                    .build()
            val media = spyk(original)
            val prepared = DirectPreparedAttachment(media)
            val message =
                UserMessage
                    .builder()
                    .text("describe")
                    .media(media)
                    .build()
            val expected = jsonMapper().writeValueAsString(directFileInputItem(message))
            val readsBefore = 4
            repeat(3) {
                assertEquals(expected, jsonMapper().writeValueAsString(directFileInputItem(message, prepared)))
                directFileTransportMessage(message, prepared)
                directFileInputItem(UserMessage.builder().media(media).build(), prepared)
            }
            verify(exactly = readsBefore) { media.data }
        }
    }
}
