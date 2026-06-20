package de.heckenmann.visualagent.ui.panels.tests

import de.heckenmann.visualagent.ui.panels.FxTestSupport
import de.heckenmann.visualagent.ui.panels.chat.ChatMessage
import de.heckenmann.visualagent.ui.panels.chat.ChatMessageRenderer
import de.heckenmann.visualagent.ui.panels.chat.ImageMessageData
import de.heckenmann.visualagent.ui.panels.chat.ToolMessageData
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMessageRendererTest {
    @Test
    fun `renders user assistant loading grouped and tool rows`() =
        FxTestSupport.run {
            var previousRole: String? = null
            var retried = false
            val renderer =
                ChatMessageRenderer(
                    loadingToken = LOADING,
                    timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()),
                    previousRole = { previousRole },
                    retryAtRow = { retried = true },
                )

            val userRow = renderer.createMessageRow(ChatMessage("user", "Hello"), 0)
            previousRole = "assistant"
            val grouped = renderer.createMessageRow(ChatMessage("assistant", "Again"), 1)
            val loading = renderer.createMessageRow(ChatMessage("assistant", LOADING), 0)
            val tool =
                renderer.createMessageRow(
                    ChatMessage(
                        role = "assistant",
                        content = "tool result",
                        isToolEvent = true,
                        toolData =
                            ToolMessageData(
                                toolId = "file:read",
                                status = "error",
                                durationMillis = 12,
                                inputJson = """{"path":"x"}""",
                                resultContent = "output",
                                resultError = "failed",
                            ),
                    ),
                    0,
                )

            assertTrue(userRow.styleClass.contains("chat-row-user"))
            assertTrue(grouped.styleClass.contains("chat-row-grouped"))
            assertTrue(descendants(loading).filterIsInstance<Label>().any { it.text == "Main agent is working" })
            val details = descendants(tool).filterIsInstance<Label>().first { it.styleClass.contains("chat-tool-details") }
            assertFalse(details.isVisible)
            descendants(tool).filterIsInstance<Button>().first { it.styleClass.contains("chat-tool-toggle") }.fire()
            assertTrue(details.isVisible)
            descendants(grouped).filterIsInstance<Button>().first { it.tooltip?.text == "Retry" }.fire()
            assertTrue(retried)
            assertEquals("failed", toolDataError(tool))
        }

    @Test
    fun `renders immutable image history rows as image previews`() =
        FxTestSupport.run {
            val renderer =
                ChatMessageRenderer(
                    loadingToken = LOADING,
                    timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()),
                    previousRole = { null },
                    retryAtRow = {},
                )

            val row =
                renderer.createMessageRow(
                    ChatMessage(
                        role = "assistant",
                        content = "Canvas snapshot (PNG)",
                        imageData =
                            ImageMessageData(
                                mimeType = "image/png",
                                dataUrl = "data:image/png;base64,$TINY_PNG_BASE64",
                                width = 1,
                                height = 1,
                            ),
                    ),
                    0,
                )

            val imageView = descendants(row).filterIsInstance<ImageView>().single()
            assertTrue(imageView.image.width > 0.0)
            assertTrue(descendants(row).filterIsInstance<Label>().any { it.text == "Canvas snapshot (PNG) · 1x1" })
        }

    private fun descendants(root: Parent): List<javafx.scene.Node> =
        root.childrenUnmodifiable.flatMap { child ->
            listOf(child) + if (child is Parent) descendants(child) else emptyList()
        }

    private fun toolDataError(row: HBox): String =
        descendants(row)
            .filterIsInstance<Label>()
            .first { it.styleClass.contains("chat-tool-details") }
            .text
            .substringAfter("Error: ")

    companion object {
        private const val LOADING = "__loading__"
        private const val TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="

        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
