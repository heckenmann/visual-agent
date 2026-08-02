package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.canvas.CanvasTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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

        assertEquals(false, result.success)
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
        val deleted = tool.execute("""{"action":"deleteSelectedFigures"}""")

        assertTrue(selected.success)
        assertTrue(deleted.success)
        assertEquals(
            listOf("drawRect", "selectAt", "moveFigure", "resizeFigure", "deleteSelectedFigures"),
            canvas.actions,
        )
    }

    @Test
    fun `select with index still works and select with indices supports multi-selection`() {
        val canvas = FakeCanvasOperations()
        val tool = CanvasTool(canvas, FakeConversationStore())

        tool.execute("""{"action":"select","index":2}""")
        tool.execute("""{"action":"select","indices":[1,3,5]}""")

        assertEquals(listOf("select:[2]", "select:[1, 3, 5]"), canvas.actions)
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
        assertEquals(false, unsupported.success)
        assertEquals("Unsupported canvas action", unsupported.error)
    }
}
