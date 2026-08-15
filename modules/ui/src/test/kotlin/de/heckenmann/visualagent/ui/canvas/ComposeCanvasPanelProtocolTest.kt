package de.heckenmann.visualagent.ui.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.CanvasDocumentReference
import de.heckenmann.visualagent.protocol.CanvasImageSnapshot
import de.heckenmann.visualagent.protocol.CanvasPort
import de.heckenmann.visualagent.protocol.CanvasSnapshot
import de.heckenmann.visualagent.protocol.WorkspaceFile
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/** Verifies that the canvas panel can render with transport-neutral ports. */
class ComposeCanvasPanelProtocolTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel renders the current canvas summary`() {
        val canvas = mockk<CanvasPort>(relaxed = true)
        val empty = CanvasSnapshot(0, 100, true, figures = emptyList())
        every { canvas.snapshot() } returns empty
        every { canvas.drawRect(any(), any(), any(), any(), any(), any()) } returns empty
        every { canvas.drawCircle(any(), any(), any(), any()) } returns empty
        every { canvas.drawText(any(), any(), any(), any()) } returns empty
        every { canvas.saveDocument(any()) } returns CanvasDocumentReference("id", "canvas.canvas", "application/json", "hash")
        every { canvas.captureImage(any()) } returns CanvasImageSnapshot("png", "image/png", byteArrayOf(), 1, 1)
        val workspace = mockk<WorkspaceFilePort>(relaxed = true)
        every { workspace.createManagedFile(any(), any(), any(), any()) } returns
            WorkspaceFile("id", "canvas/capture.png", "capture.png", "image/png", 0, "hash", "now", "now")
        val activity = mockk<ActivityPort>(relaxed = true)

        composeTestRule.setContent {
            MaterialTheme {
                CanvasPanel(canvas, workspace, ComposeModalRequester { }, activity)
            }
        }

        composeTestRule.onNodeWithText("Figures: 0").assertExists()
        composeTestRule.onNodeWithText("canvas.canvas").assertExists()
        composeTestRule.onNodeWithText("canvas-capture.png").assertExists()
        composeTestRule.onNodeWithContentDescription("Add rectangle").performClick()
        composeTestRule.onNodeWithContentDescription("Add circle").performClick()
        composeTestRule.onNodeWithContentDescription("Add text").performClick()
        composeTestRule.onNodeWithContentDescription("Save canvas document").performClick()
        composeTestRule.onNodeWithContentDescription("Capture canvas").performClick()
        verify { canvas.drawRect(any(), any(), any(), any(), any(), any()) }
        verify { canvas.drawCircle(any(), any(), any(), any()) }
        verify { canvas.drawText(any(), any(), any(), any()) }
        verify { canvas.saveDocument("canvas.canvas") }
        verify { canvas.captureImage("png") }
    }
}
