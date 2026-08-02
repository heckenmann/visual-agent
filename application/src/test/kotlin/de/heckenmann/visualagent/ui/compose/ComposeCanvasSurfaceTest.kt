@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.canvas.CanvasSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class ComposeCanvasSurfaceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `canvas surface renders with empty snapshot`() {
        val canvas = mockk<CanvasOperations>()
        every { canvas.snapshot() } returns CanvasSnapshot(figureCount = 0, zoomPercent = 100, gridVisible = true, figures = emptyList())
        every { canvas.selectFigures(any()) } returns
            CanvasSnapshot(figureCount = 0, zoomPercent = 100, gridVisible = true, figures = emptyList())

        composeTestRule.setContent {
            MaterialTheme {
                CanvasSurface(
                    snapshot = CanvasSnapshot(figureCount = 0, zoomPercent = 100, gridVisible = true, figures = emptyList()),
                    canvasOperations = canvas,
                    mode = CanvasInteractionMode.Select,
                    imageBytesForPath = { null },
                    onSnapshotChanged = {},
                    modifier = Modifier,
                )
            }
        }

        composeTestRule.waitForIdle()
    }
}
