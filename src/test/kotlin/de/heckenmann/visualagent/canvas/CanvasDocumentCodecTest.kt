package de.heckenmann.visualagent.canvas

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanvasDocumentCodecTest {
    @Test
    fun `encode and decode round trip preserves figures and view state`() {
        val figures =
            listOf(
                CanvasFigureSnapshot(
                    index = 0,
                    type = "text",
                    x = 10.0,
                    y = 20.0,
                    width = 80.0,
                    height = 24.0,
                    content = "Hi",
                    color = "#fff",
                ),
                CanvasFigureSnapshot(index = 1, type = "rectangle", x = 0.0, y = 0.0, width = 50.0, height = 50.0, color = "#000"),
            )
        val snapshot =
            CanvasSnapshot(figureCount = 2, zoomPercent = 150, gridVisible = false, selectedFigureIndices = setOf(1), figures = figures)

        val text = CanvasDocumentCodec.encode(snapshot)
        val document = CanvasDocumentCodec.decode(text)

        assertEquals(CanvasDocumentCodec.VERSION, document.version)
        assertEquals(150, document.zoomPercent)
        assertEquals(false, document.gridVisible)
        assertEquals(2, document.figures.size)
        assertEquals(listOf(0, 1), document.figures.map { it.index })
        assertEquals("Hi", document.figures[0].content)
    }

    @Test
    fun `decode reindexes figures in document order`() {
        val figures =
            listOf(
                CanvasFigureSnapshot(index = 99, type = "circle", x = 5.0, y = 5.0, width = 10.0, height = 10.0),
                CanvasFigureSnapshot(index = 42, type = "line", x = 1.0, y = 1.0, width = 5.0, height = 5.0),
            )
        val snapshot = CanvasSnapshot(figureCount = 2, zoomPercent = 100, gridVisible = true, figures = figures)

        val document = CanvasDocumentCodec.decode(CanvasDocumentCodec.encode(snapshot))

        assertEquals(listOf(0, 1), document.figures.map { it.index })
    }

    @Test
    fun `decode rejects unsupported versions`() {
        val json =
            """
            {
                "version": 999,
                "zoomPercent": 100,
                "gridVisible": true,
                "figures": []
            }
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            CanvasDocumentCodec.decode(json)
        }
    }

    @Test
    fun `decode tolerates unknown fields`() {
        val json =
            """
            {
                "version": ${CanvasDocumentCodec.VERSION},
                "zoomPercent": 100,
                "gridVisible": true,
                "figures": [],
                "futureField": true
            }
            """.trimIndent()

        val document = CanvasDocumentCodec.decode(json)
        assertEquals(emptyList(), document.figures)
    }
}
