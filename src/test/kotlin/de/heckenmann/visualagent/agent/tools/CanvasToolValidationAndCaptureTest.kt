package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.canvas.CanvasTool
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasToolValidationAndCaptureTest {
    @Test
    fun `missing required fields return a tool failure`() {
        val tool = CanvasTool(FakeCanvasOperations(), FakeConversationStore())

        val result = tool.execute("""{"action":"drawText","x":1,"y":2}""")

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("Missing required field"))
        assertTrue(result.error.orEmpty().contains("'text'"))
    }

    @Test
    fun `insert image rejects paths outside workspace`() {
        val tool = CanvasTool(FakeCanvasOperations(), FakeConversationStore())

        val result = tool.execute("""{"action":"insertImage","path":"../outside.png"}""")

        assertFalse(result.success)
        assertEquals("canvas", result.toolId)
    }

    @Test
    fun `capture image persists immutable history image and returns compact result`() {
        val canvas = FakeCanvasOperations()
        val store = FakeConversationStore()
        val tool = CanvasTool(canvas, store)

        val result = tool.execute("""{"action":"captureImage","format":"png"}""", mapOf("sessionId" to "main"))

        assertTrue(result.success)
        assertEquals(listOf("captureImage:png"), canvas.actions)
        assertFalse(result.content.contains("base64"))
        val saved = store.saved.single()
        assertEquals("main", saved.sessionId)
        assertEquals("assistant", saved.role)
        assertEquals("Canvas snapshot (PNG)", saved.content)
        assertTrue(saved.metadata.orEmpty().contains(""""type":"image""""))
        assertTrue(saved.metadata.orEmpty().contains(""""source":"canvas""""))
        assertTrue(saved.metadata.orEmpty().contains("data:image/png;base64,AQID"))
        assertTrue(saved.metadata.orEmpty().contains(""""immutable":true"""))
    }

    @Test
    fun `capture image reports unsupported formats as tool failure`() {
        val tool = CanvasTool(FakeCanvasOperations(), FakeConversationStore())

        val result = tool.execute("""{"action":"captureImage","format":"jpg"}""")

        assertFalse(result.success)
        assertEquals("Unsupported canvas image format: jpg", result.error)
    }
}
