package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.ui.panels.canvas.CanvasFigureSnapshot
import de.heckenmann.visualagent.ui.panels.canvas.CanvasOperations
import de.heckenmann.visualagent.ui.panels.canvas.CanvasSnapshot
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
        val tool = CanvasTool(FakeCanvasOperations())

        val result = tool.execute("""{"action":"get"}""")
        val content = Json.parseToJsonElement(result.content).jsonObject

        assertTrue(result.success)
        assertEquals("0", content["figureCount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `draw actions delegate to canvas operations`() {
        val canvas = FakeCanvasOperations()
        val tool = CanvasTool(canvas)

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
    fun `clear and unsupported actions return deterministic results`() {
        val canvas = FakeCanvasOperations()
        val tool = CanvasTool(canvas)

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
        val tool = CanvasTool(FakeCanvasOperations())

        val result = tool.execute("""{"action":"drawText","x":1,"y":2}""")

        assertFalse(result.success)
        assertEquals("Missing required field 'text'", result.error)
    }

    @Test
    fun `insert image rejects paths outside workspace`() {
        val tool = CanvasTool(FakeCanvasOperations())

        val result = tool.execute("""{"action":"insertImage","path":"../outside.png"}""")

        assertFalse(result.success)
        assertEquals("canvas", result.toolId)
    }

    private class FakeCanvasOperations : CanvasOperations {
        val actions = mutableListOf<String>()
        private var figures = emptyList<CanvasFigureSnapshot>()

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

        private fun snapshotOf(figures: List<CanvasFigureSnapshot>): CanvasSnapshot =
            CanvasSnapshot(
                figureCount = figures.size,
                zoomPercent = 100,
                gridVisible = true,
                figures = figures,
            )
    }
}
