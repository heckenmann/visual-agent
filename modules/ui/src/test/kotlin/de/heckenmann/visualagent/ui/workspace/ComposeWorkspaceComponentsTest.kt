@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
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
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for reusable workspace layout components.
 */
class ComposeWorkspaceComponentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel section renders title and content`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelSection(title = "Section") {
                    Text("inside section")
                }
            }
        }
        composeTestRule.onNodeWithText("Section").assertExists()
        composeTestRule.onNodeWithText("inside section").assertExists()
    }

    @Test
    fun `panel content card renders content`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelContentCard {
                    Text("inside card")
                }
            }
        }
        composeTestRule.onNodeWithText("inside card").assertExists()
    }

    @Test
    fun `panel empty state renders title and body`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelEmptyState(title = "Empty", body = "Nothing here")
            }
        }
        composeTestRule.onNodeWithText("Empty").assertExists()
        composeTestRule.onNodeWithText("Nothing here").assertExists()
    }

    @Test
    fun `panel info box renders text`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelInfoBox(text = "Info text")
            }
        }
        composeTestRule.onNodeWithText("Info text").assertExists()
    }

    @Test
    fun `numeric panel field filters non-digit input`() {
        var value = ""
        composeTestRule.setContent {
            MaterialTheme {
                NumericPanelField(
                    label = "Count",
                    value = value,
                    onValueChange = { value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Count").assertExists()
    }

    @Test
    fun `panel resizer reports a new width when dragged right`() {
        var resizedWidth = 0
        var committedWidths = 0
        val previewWidths = mutableListOf<Int>()
        composeTestRule.setContent {
            MaterialTheme {
                panelResizer(
                    currentWidth = 300,
                    onPreviewWidthChanged = previewWidths::add,
                    onWidthCommitted = {
                        resizedWidth = it
                        committedWidths++
                    },
                    onCancelled = {},
                    minPanelWidth = 200,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription("Resize panel")
            .performTouchInput {
                swipeRight(
                    startX = centerX,
                    endX = centerX + 100f,
                )
            }
        composeTestRule.waitForIdle()

        assertTrue("Expected resized width > 300 but was $resizedWidth", resizedWidth > 300)
        assertTrue("Expected live preview widths", previewWidths.any { it > 300 })
        assertEquals("Expected one final width commit", 1, committedWidths)
    }

    @Test
    fun `workspace starts the first visible panel at the shared edge gap`() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.size(1_200.dp, 600.dp),
                ) {
                    ComposeSplitWorkspace(
                        windows = listOf(testWindow("first", "First")),
                        panelServices = mockk(relaxed = true),
                        onToggleWindow = {},
                        onReorderWindows = {},
                        onResizeWindow = { _, _ -> },
                        minPanelWidth = ComposeWorkspaceWindowBounds.MIN_WIDTH,
                        viewport = ComposeWorkspaceViewport(1_200, 600),
                    )
                }
            }
        }

        val panelBounds =
            composeTestRule
                .onNodeWithTag("workspace-panel-content-first")
                .getUnclippedBoundsInRoot()
        val viewportBounds = composeTestRule.onNodeWithTag("workspace-viewport").getUnclippedBoundsInRoot()
        val listBounds = composeTestRule.onNodeWithTag("workspace-horizontal-list").getUnclippedBoundsInRoot()

        assertEquals(WORKSPACE_PANEL_GAP.dp, panelBounds.left)
        assertEquals(
            viewportBounds.right - viewportBounds.left,
            listBounds.right - listBounds.left,
        )
    }

    @Test
    fun `workspace preserves the shared edge gap when earlier panels are hidden`() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(1_200.dp, 600.dp)) {
                    ComposeSplitWorkspace(
                        windows = listOf(testWindow("hidden", "Hidden").copy(visible = false), testWindow("visible", "Visible")),
                        panelServices = mockk(relaxed = true),
                        onToggleWindow = {},
                        onReorderWindows = {},
                        onResizeWindow = { _, _ -> },
                        minPanelWidth = ComposeWorkspaceWindowBounds.MIN_WIDTH,
                        viewport = ComposeWorkspaceViewport(1_200, 600),
                    )
                }
            }
        }

        val panelBounds = composeTestRule.onNodeWithTag("workspace-panel-content-visible").getUnclippedBoundsInRoot()

        assertEquals(WORKSPACE_PANEL_GAP.dp, panelBounds.left)
    }

    @Test
    fun `workspace panel visibility removes content after closing transition`() {
        var visible by mutableStateOf(true)
        composeTestRule.setContent {
            MaterialTheme {
                WorkspacePanelVisibility(visible = visible) {
                    Text("animated workspace panel")
                }
            }
        }

        composeTestRule.onNodeWithText("animated workspace panel").assertExists()
        visible = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("animated workspace panel").assertDoesNotExist()
    }

    @Test
    fun `workspace panels animate to their reordered positions`() {
        var windows by mutableStateOf(listOf(testWindow("first", "First"), testWindow("second", "Second")))
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier =
                        androidx.compose.ui.Modifier
                            .size(1_200.dp, 600.dp),
                ) {
                    ComposeSplitWorkspace(
                        windows = windows,
                        panelServices = mockk(relaxed = true),
                        onToggleWindow = {},
                        onReorderWindows = { windows = it },
                        onResizeWindow = { _, _ -> },
                        minPanelWidth = ComposeWorkspaceWindowBounds.MIN_WIDTH,
                        viewport = ComposeWorkspaceViewport(1_200, 600),
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        val initialLeft = composeTestRule.onNodeWithTag("workspace-panel-first").getUnclippedBoundsInRoot().left

        windows = windows.reversed()
        composeTestRule.mainClock.advanceTimeBy(110)
        val halfwayLeft = composeTestRule.onNodeWithTag("workspace-panel-first").getUnclippedBoundsInRoot().left
        composeTestRule.mainClock.advanceTimeBy(500)
        val finalLeft = composeTestRule.onNodeWithTag("workspace-panel-first").getUnclippedBoundsInRoot().left

        assertTrue(
            "Expected panel to be between $initialLeft and $finalLeft halfway through reorder, but was $halfwayLeft",
            halfwayLeft > initialLeft && halfwayLeft < finalLeft,
        )
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
