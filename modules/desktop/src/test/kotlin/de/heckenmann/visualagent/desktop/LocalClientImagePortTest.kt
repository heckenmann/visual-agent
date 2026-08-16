package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ConversationImageResolution
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Verifies that client-local image sources stay in the desktop process. */
class LocalClientImagePortTest {
    @Test
    fun `loads a client file and validates its mime type`() =
        runBlocking {
            val path = Files.createTempFile("client-image", ".png")
            try {
                Files.write(path, PNG_BYTES)

                val result = LocalClientImagePort().resolveImage("client-file:$path")

                val loaded = assertIs<ConversationImageResolution.Loaded>(result)
                assertEquals("image/png", loaded.mimeType)
                assertTrue(loaded.bytes.contentEquals(PNG_BYTES))
            } finally {
                Files.deleteIfExists(path)
            }
        }

    @Test
    fun `rejects server sources instead of reading them as client files`() =
        runBlocking {
            val result = LocalClientImagePort().resolveImage("server-file:generated/chart.png")

            assertIs<ConversationImageResolution.Rejected>(result)
            Unit
        }

    private companion object {
        val PNG_BYTES =
            Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            )
    }
}
