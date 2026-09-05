@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.workspace.*
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests workspace overflow behavior during resize and viewport changes. */
class ComposeWorkspaceOverflowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `rightmost panel remains reachable while resize preview creates overflow`() {
        var committedWidths = 0
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(700.dp, 600.dp)) {
                    ComposeSplitWorkspace(
                        windows = listOf(testWindow("first", "First"), testWindow("second", "Second")),
                        panelServices = mockk(relaxed = true),
                        onToggleWindow = {},
                        onReorderWindows = {},
                        onResizeWindow = { _, _ -> committedWidths++ },
                        minPanelWidth = ComposeWorkspaceWindowBounds.MIN_WIDTH,
                        viewport = ComposeWorkspaceViewport(700, 600),
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workspace-panel-resizer-second").performTouchInput {
            down(center)
            moveBy(Offset(x = 100f, y = 0f))
        }
        composeTestRule.waitForIdle()

        val viewportBounds = composeTestRule.onNodeWithTag("workspace-viewport").getBoundsInRoot()
        val resizerBounds = composeTestRule.onNodeWithTag("workspace-panel-resizer-second").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected overflow scrollbar during preview",
            composeTestRule
                .onNodeWithTag("workspace-horizontal-scrollbar")
                .fetchSemanticsNode()
                .boundsInRoot.width > 0f,
        )
        assertTrue("Expected active resizer to remain visible", resizerBounds.right <= viewportBounds.right + 1.dp)
        assertEquals("Preview must not commit before pointer release", 0, committedWidths)

        composeTestRule.onNodeWithTag("workspace-panel-resizer-second").performTouchInput { up() }
        composeTestRule.waitForIdle()
        assertEquals("Expected one final width commit", 1, committedWidths)
    }

    @Test
    fun `workspace overflow follows viewport width changes`() {
        var viewport by mutableStateOf(ComposeWorkspaceViewport(700, 600))
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(viewport.width.dp, viewport.height.dp)) {
                    ComposeSplitWorkspace(
                        windows = listOf(testWindow("first", "First"), testWindow("second", "Second")),
                        panelServices = mockk(relaxed = true),
                        onToggleWindow = {},
                        onReorderWindows = {},
                        onResizeWindow = { _, _ -> },
                        minPanelWidth = ComposeWorkspaceWindowBounds.MIN_WIDTH,
                        viewport = viewport,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workspace-horizontal-scrollbar").assertDoesNotExist()
        viewport = ComposeWorkspaceViewport(600, 600)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workspace-horizontal-scrollbar").assertExists()
        viewport = ComposeWorkspaceViewport(700, 600)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workspace-horizontal-scrollbar").assertDoesNotExist()
    }

    @Test
    fun `workspace row width includes visible panels and spacing`() {
        assertEquals(0, workspaceRowWidth(emptyList()))
        assertEquals(WORKSPACE_PANEL_GAP + 300 + WORKSPACE_PANEL_RESIZER_WIDTH, workspaceRowWidth(listOf(300)))
        assertEquals(
            WORKSPACE_PANEL_GAP + 300 + 280 + (2 * WORKSPACE_PANEL_RESIZER_WIDTH) + WORKSPACE_PANEL_GAP,
            workspaceRowWidth(listOf(300, 280)),
        )
    }

    @Test
    fun `resizer scroll delta follows both viewport edges`() {
        assertEquals(-20, resizerScrollDelta(resizerEdge = 80, viewportStart = 100, viewportEnd = 500))
        assertEquals(0, resizerScrollDelta(resizerEdge = 300, viewportStart = 100, viewportEnd = 500))
        assertEquals(20, resizerScrollDelta(resizerEdge = 520, viewportStart = 100, viewportEnd = 500))
    }

    @Test
    fun `workspace panel gap converts to physical pixels`() {
        val density = Density(density = 2f)

        assertEquals(32, density.workspacePanelTrailingGapPx(isLast = false))
        assertEquals(0, density.workspacePanelTrailingGapPx(isLast = true))
    }

    private fun testWindow(
        id: String,
        title: String,
    ) = ComposeWorkspaceWindow(
        id = id,
        icon = id,
        title = title,
        subtitle = title,
        bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200),
    )
}
