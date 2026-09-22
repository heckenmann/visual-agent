package de.heckenmann.visualagent.ui.conversation

import org.junit.Test
import kotlin.test.assertNotNull

/** Tests for inline conversation image attachment decoding. */
class ConversationImageAttachmentsTest {
    @Test
    fun `decodes a validated canvas image attachment`() {
        val canvasImage =
            "data:image/png;base64," +
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="

        assertNotNull(decodeEmbeddedImage(canvasImage))
    }
}
