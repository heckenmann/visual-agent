@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.canvas.CanvasSnapshot
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class ComposeCanvasPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `canvas panel renders toolbar and surface`() {
        val canvas = mockk<CanvasOperations>()
        every { canvas.snapshot() } returns CanvasSnapshot(figureCount = 0, zoomPercent = 100, gridVisible = true, figures = emptyList())
        val workspace = mockk<WorkspaceFileService>()

        composeTestRule.setContent {
            MaterialTheme {
                CanvasPanel(canvas, workspace, ComposeModalRequester { }, ToolEventBus())
            }
        }

        composeTestRule.onNodeWithText("Figures: 0").assertExists()
    }
}
