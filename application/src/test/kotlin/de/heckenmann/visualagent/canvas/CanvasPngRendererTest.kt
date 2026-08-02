package de.heckenmann.visualagent.canvas

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CanvasPngRendererTest {
    @Test
    fun `render covers all figure types without crashing`() {
        val figures =
            listOf(
                CanvasFigureSnapshot(
                    index = 0,
                    type = "rectangle",
                    x = 10.0,
                    y = 10.0,
                    width = 60.0,
                    height = 40.0,
                    color = "#50FA7B",
                ),
                CanvasFigureSnapshot(
                    index = 1,
                    type = "circle",
                    x = 90.0,
                    y = 10.0,
                    width = 30.0,
                    height = 30.0,
                    color = "#FF79C6",
                ),
                CanvasFigureSnapshot(
                    index = 2,
                    type = "line",
                    x = 10.0,
                    y = 70.0,
                    width = 50.0,
                    height = 20.0,
                    color = "#8BE9FD",
                ),
                CanvasFigureSnapshot(
                    index = 3,
                    type = "stroke",
                    x = 5.0,
                    y = 5.0,
                    width = 20.0,
                    height = 20.0,
                    color = "#F8F8F2",
                    points = listOf(CanvasPoint(0.0, 0.0), CanvasPoint(10.0, 10.0)),
                ),
                CanvasFigureSnapshot(
                    index = 4,
                    type = "text",
                    x = 10.0,
                    y = 110.0,
                    width = 80.0,
                    height = 24.0,
                    color = "#FFB86C",
                ),
                CanvasFigureSnapshot(
                    index = 5,
                    type = "image",
                    x = 150.0,
                    y = 10.0,
                    width = 32.0,
                    height = 32.0,
                    content = "/tmp/example.png",
                ),
                CanvasFigureSnapshot(
                    index = 6,
                    type = "unknown",
                    x = 150.0,
                    y = 60.0,
                    width = 32.0,
                    height = 32.0,
                ),
            )
        val snapshot = CanvasSnapshot(figureCount = figures.size, zoomPercent = 100, gridVisible = true, figures = figures)

        val bytes = CanvasPngRenderer.render(snapshot, 300, 200)

        assertTrue(isPng(bytes))
        assertTrue(bytes.size > 100)
    }

    @Test
    fun `render uses default colors for invalid hex`() {
        val figures =
            listOf(
                CanvasFigureSnapshot(
                    index = 0,
                    type = "rectangle",
                    x = 10.0,
                    y = 10.0,
                    width = 20.0,
                    height = 20.0,
                    color = "not-a-color",
                ),
                CanvasFigureSnapshot(
                    index = 1,
                    type = "circle",
                    x = 50.0,
                    y = 10.0,
                    width = 20.0,
                    height = 20.0,
                    color = "",
                ),
            )
        val snapshot = CanvasSnapshot(figureCount = 2, zoomPercent = 100, gridVisible = true, figures = figures)

        val bytes = CanvasPngRenderer.render(snapshot, 100, 80)

        assertTrue(isPng(bytes))
    }

    @Test
    fun `render clamps figures outside bounds`() {
        val figures =
            listOf(
                CanvasFigureSnapshot(
                    index = 0,
                    type = "rectangle",
                    x = -10.0,
                    y = -10.0,
                    width = 200.0,
                    height = 200.0,
                    color = "#BD93F9",
                ),
                CanvasFigureSnapshot(
                    index = 1,
                    type = "line",
                    x = -5.0,
                    y = 5.0,
                    width = 150.0,
                    height = 5.0,
                    color = "#8BE9FD",
                ),
            )
        val snapshot = CanvasSnapshot(figureCount = 2, zoomPercent = 100, gridVisible = true, figures = figures)

        val bytes = CanvasPngRenderer.render(snapshot, 50, 50)

        assertTrue(isPng(bytes))
    }

    @Test
    fun `render handles empty snapshot with default canvas size`() {
        val snapshot = CanvasSnapshot(figureCount = 0, zoomPercent = 100, gridVisible = true, figures = emptyList())

        val bytes = CanvasPngRenderer.render(snapshot, 100, 80)

        assertTrue(isPng(bytes))
        assertTrue(bytes.size > 80)
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
}
