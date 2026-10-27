package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.error.CanvasOperationException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanvasCaptureLimitsTest {
    @Test
    fun `capture rejects overflowing extents before allocation`() {
        listOf(50_000.0, 100_000.0, Double.MAX_VALUE).forEach { extent ->
            assertFailsWith<CanvasOperationException> { CanvasCaptureLimits.dimensions(snapshot(x = extent, y = extent)) }
        }
    }

    @Test
    fun `renderer rejects excessive dimensions and pixel count before allocation`() {
        listOf(50_000 to 50_000, Int.MAX_VALUE to Int.MAX_VALUE, 4096 to 4096, 0 to 1, 1 to -1).forEach { (width, height) ->
            assertFailsWith<CanvasOperationException> { CanvasPngRenderer.render(snapshot(), width, height) }
        }
        CanvasCaptureLimits.validateSize(4096, 1024)
    }

    @Test
    fun `nonfinite geometry and stroke points are rejected`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            assertFailsWith<CanvasOperationException> { CanvasCaptureLimits.dimensions(snapshot(x = value)) }
            val source = snapshot()
            val invalid = source.copy(figures = listOf(source.figures.single().copy(points = listOf(CanvasPoint(value, 0.0)))))
            assertFailsWith<CanvasOperationException> { CanvasPngRenderer.render(invalid, 800, 600) }
        }
    }

    @Test
    fun `safe captures retain origin dimensions and round extents outwards`() {
        assertEquals(800 to 600, CanvasCaptureLimits.dimensions(snapshot()))
        assertEquals(851 to 600, CanvasCaptureLimits.dimensions(snapshot(x = 800.5)))
    }

    private fun snapshot(
        x: Double = 0.0,
        y: Double = 0.0,
    ) = CanvasSnapshot(
        figureCount = 1,
        zoomPercent = 100,
        gridVisible = true,
        figures = listOf(CanvasFigureSnapshot(0, "rectangle", x, y, 10.0, 10.0)),
    )
}
