package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.canvas.CanvasTool
import de.heckenmann.visualagent.canvas.CanvasDocumentReference
import de.heckenmann.visualagent.canvas.CanvasFigureSnapshot
import de.heckenmann.visualagent.canvas.CanvasImageSnapshot
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.canvas.CanvasPoint
import de.heckenmann.visualagent.canvas.CanvasSnapshot
import de.heckenmann.visualagent.knowledge.ConversationRecord
import de.heckenmann.visualagent.knowledge.ConversationStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasToolTest {
    @Test
    fun `get returns current canvas snapshot`() {
        val tool = CanvasTool(FakeCanvasOperations(), FakeConversationStore())

        val result = tool.execute("""{"action":"get"}""")
        val content = Json.parseToJsonElement(result.content).jsonObject

        assertTrue(result.success)
        assertEquals("0", content["figureCount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `draw actions delegate to canvas operations`() {
        val canvas = FakeCanvasOperations()
        val tool = CanvasTool(canvas, FakeConversationStore())

        tool.execute("""{"action":"drawText","text":"Hello","x":1,"y":2}""")
        tool.execute("""{"action":"drawLine","x1":1,"y1":2,"x2":3,"y2":4}""")
        tool.execute("""{"action":"drawCircle","centerX":8,"centerY":9,"radius":3}""")
        val result = tool.execute("""{"action":"drawRect","x":10,"y":20,"width":100,"height":50,"fillColor":"#ff0000"}""")
        val content = Json.parseToJsonElement(result.content).jsonObject

        assertTrue(result.success)
        assertEquals(listOf("drawText", "drawLine", "drawCircle", "drawRect"), canvas.actions)
        assertEquals("1", content["figureCount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `drawStroke action delegates to canvas operations`() {
        val canvas = FakeCanvasOperations()
        val tool = CanvasTool(canvas, FakeConversationStore())

        val result =
            tool.execute(
                """{"action":"drawStroke","points":[{"x":0,"y":0},{"x":10,"y":5},{"x":20,"y":12}],"color":"#ff00ff","width":3.0}""",
            )

        assertTrue(result.success)
        assertEquals(listOf("drawStroke"), canvas.actions)
        val figure = canvas.lastFigure
        assertEquals("stroke", figure.type)
        assertEquals("#ff00ff", figure.color)
        assertEquals(3.0, figure.strokeWidth)
        assertEquals(3, figure.points.size)
    }

    @Test
    fun `drawStroke rejects fewer than two points`() {
        val tool = CanvasTool(FakeCanvasOperations(), FakeConversationStore())

        val result = tool.execute("""{"action":"drawStroke","points":[{"x":0,"y":0}]}""")

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("points"))
    }

    @Test
    fun `selection mutation actions delegate to canvas operations`() {
        val canvas = FakeCanvasOperations()
        val tool = CanvasTool(canvas, FakeConversationStore())

        tool.execute("""{"action":"drawRect","x":10,"y":20,"width":100,"height":50}""")
        val selected = tool.execute("""{"action":"selectAt","x":15,"y":25}""")
        tool.execute("""{"action":"moveFigure","index":0,"deltaX":5,"deltaY":6}""")
        tool.execute("""{"action":"resizeFigure","index":0,"width":80,"height":40}""")
        val deleted = tool.execute("""{"action":"deleteFigure","index":0}""")

        assertTrue(selected.success)
        assertTrue(deleted.success)
        assertEquals(
            listOf("drawRect", "selectAt", "moveFigure", "resizeFigure", "deleteFigure"),
            canvas.actions,
        )
    }

    @Test
    fun `save and open document actions delegate to canvas operations`() {
        val canvas = FakeCanvasOperations()
        val tool = CanvasTool(canvas, FakeConversationStore())

        val saved = tool.execute("""{"action":"saveDocument","name":"diagram"}""")
        val opened = tool.execute("""{"action":"openDocument","id":"canvas-1"}""")

        assertTrue(saved.success)
        assertTrue(opened.success)
        assertEquals(listOf("saveDocument:diagram", "openDocument:canvas-1:null"), canvas.actions)
        assertTrue(saved.content.contains("canvas/diagram.canvas"))
    }

    @Test
    fun `clear and unsupported actions return deterministic results`() {
        val canvas = FakeCanvasOperations()
        val tool = CanvasTool(canvas, FakeConversationStore())

        tool.execute("""{"action":"drawText","text":"Hello","x":1,"y":2}""")
        val clear = tool.execute("""{"action":"clear"}""")
        val unsupported = tool.execute("""{"action":"unknown"}""")

        assertTrue(clear.success)
        val clearContent = Json.parseToJsonElement(clear.content).jsonObject
        assertEquals("0", clearContent["figureCount"]!!.jsonPrimitive.content)
        assertFalse(unsupported.success)
        assertEquals("Unsupported canvas action", unsupported.error)
    }

    @Test
    fun `missing required fields return a tool failure`() {
        val tool = CanvasTool(FakeCanvasOperations(), FakeConversationStore())

        val result = tool.execute("""{"action":"drawText","x":1,"y":2}""")

        assertFalse(result.success)
        assertEquals("Missing required field 'text'", result.error)
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

    private class FakeCanvasOperations : CanvasOperations {
        val actions = mutableListOf<String>()
        private var figures = emptyList<CanvasFigureSnapshot>()

        val lastFigure: CanvasFigureSnapshot
            get() = figures.last()

        override fun snapshot(): CanvasSnapshot = snapshotOf(figures)

        override fun clear(): CanvasSnapshot {
            actions += "clear"
            figures = emptyList()
            return snapshot()
        }

        override fun drawText(
            text: String,
            x: Double,
            y: Double,
            color: String,
        ): CanvasSnapshot {
            actions += "drawText"
            figures = listOf(CanvasFigureSnapshot(0, "text", x, y, 0.0, 0.0))
            return snapshot()
        }

        override fun drawRect(
            x: Double,
            y: Double,
            width: Double,
            height: Double,
            fillColor: String,
            strokeColor: String?,
        ): CanvasSnapshot {
            actions += "drawRect"
            figures = listOf(CanvasFigureSnapshot(0, "rectangle", x, y, width, height))
            return snapshot()
        }

        override fun drawLine(
            x1: Double,
            y1: Double,
            x2: Double,
            y2: Double,
            color: String,
            width: Double,
        ): CanvasSnapshot {
            actions += "drawLine"
            figures = listOf(CanvasFigureSnapshot(0, "line", x1, y1, x2 - x1, y2 - y1))
            return snapshot()
        }

        override fun drawCircle(
            centerX: Double,
            centerY: Double,
            radius: Double,
            fillColor: String,
        ): CanvasSnapshot {
            actions += "drawCircle"
            figures = listOf(CanvasFigureSnapshot(0, "circle", centerX - radius, centerY - radius, radius * 2, radius * 2))
            return snapshot()
        }

        override fun insertImage(path: String): CanvasSnapshot {
            actions += "insertImage"
            figures = listOf(CanvasFigureSnapshot(0, "image", 40.0, 40.0, 100.0, 100.0))
            return snapshot()
        }

        override fun drawStroke(
            points: List<CanvasPoint>,
            color: String,
            width: Double,
        ): CanvasSnapshot {
            actions += "drawStroke"
            require(points.size >= 2) { "drawStroke requires at least two points" }
            val first = points.first()
            val last = points.last()
            figures =
                listOf(
                    CanvasFigureSnapshot(
                        index = 0,
                        type = "stroke",
                        x = first.x,
                        y = first.y,
                        width = last.x - first.x,
                        height = last.y - first.y,
                        content = "",
                        color = color,
                        strokeWidth = width,
                        points = points,
                    ),
                )
            return snapshot()
        }

        override fun selectFigure(index: Int?): CanvasSnapshot {
            actions += "select"
            return snapshot()
        }

        override fun selectAt(
            x: Double,
            y: Double,
        ): CanvasSnapshot {
            actions += "selectAt"
            return snapshot()
        }

        override fun moveFigure(
            index: Int,
            deltaX: Double,
            deltaY: Double,
        ): CanvasSnapshot {
            actions += "moveFigure"
            figures =
                figures.mapIndexed { figureIndex, figure ->
                    if (figureIndex == index) figure.copy(x = figure.x + deltaX, y = figure.y + deltaY) else figure
                }
            return snapshot()
        }

        override fun resizeFigure(
            index: Int,
            width: Double,
            height: Double,
        ): CanvasSnapshot {
            actions += "resizeFigure"
            figures =
                figures.mapIndexed { figureIndex, figure ->
                    if (figureIndex == index) figure.copy(width = width, height = height) else figure
                }
            return snapshot()
        }

        override fun deleteFigure(index: Int): CanvasSnapshot {
            actions += "deleteFigure"
            figures = figures.filterIndexed { figureIndex, _ -> figureIndex != index }
            return snapshot()
        }

        override fun saveDocument(requestedName: String): CanvasDocumentReference {
            actions += "saveDocument:$requestedName"
            return CanvasDocumentReference(
                id = "canvas-1",
                relativePath = "canvas/$requestedName.canvas",
                mimeType = "application/vnd.visual-agent.canvas+xml",
                sha256 = "abc123",
            )
        }

        override fun openDocument(
            id: String?,
            path: String?,
        ): CanvasSnapshot {
            actions += "openDocument:$id:$path"
            figures = listOf(CanvasFigureSnapshot(0, "rectangle", 1.0, 2.0, 3.0, 4.0))
            return snapshot()
        }

        override fun captureImage(format: String): CanvasImageSnapshot {
            require(format == "png") { "Unsupported canvas image format: $format" }
            actions += "captureImage:$format"
            return CanvasImageSnapshot(
                format = "png",
                mimeType = "image/png",
                bytes = byteArrayOf(1, 2, 3),
                width = 2,
                height = 1,
            )
        }

        private fun snapshotOf(figures: List<CanvasFigureSnapshot>): CanvasSnapshot =
            CanvasSnapshot(
                figureCount = figures.size,
                zoomPercent = 100,
                gridVisible = true,
                selectedFigureIndex = null,
                figures = figures,
            )
    }

    private class FakeConversationStore : ConversationStore {
        val saved = mutableListOf<SavedMessage>()

        override fun saveConversationMessage(
            sessionId: String,
            role: String,
            content: String,
            metadata: String?,
        ): String {
            saved += SavedMessage(sessionId, role, content, metadata)
            return "message-${saved.size}"
        }

        override fun getConversationMessages(
            sessionId: String,
            limit: Int,
        ): List<ConversationRecord> = emptyList()

        override fun getConversationMessagesPage(
            sessionId: String,
            limit: Int,
            offset: Int,
        ): List<ConversationRecord> = emptyList()

        override fun searchConversationMessages(
            sessionId: String,
            query: String,
            limit: Int,
        ): List<ConversationRecord> = emptyList()

        override fun deleteConversationMessages(sessionId: String): Int = 0
    }

    private data class SavedMessage(
        val sessionId: String,
        val role: String,
        val content: String,
        val metadata: String?,
    )
}
