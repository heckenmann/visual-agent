@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeRailTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `rail renders panel toggle buttons`() {
        val windows =
            listOf(
                ComposeWorkspaceWindow(
                    id = "chat",
                    icon = "chat",
                    title = "Chat",
                    subtitle = "",
                    bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200),
                    visible = true,
                ),
                ComposeWorkspaceWindow(
                    id = "todos",
                    icon = "todos",
                    title = "Todos",
                    subtitle = "",
                    bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200),
                    visible = false,
                ),
            )

        composeTestRule.setContent {
            MaterialTheme {
                ComposeRail(
                    windows = windows,
                    onToggleWindow = {},
                    onReorderWindows = {},
                    onPanelWidthChanged = { _, _ -> },
                    onCloseApplication = {},
                    modalRequester = ComposeModalRequester { },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Toggle Chat").assertExists()
        composeTestRule.onNodeWithContentDescription("Toggle Todos").assertExists()
        composeTestRule.onNodeWithContentDescription("Close application").assertExists()
    }

    @Test
    fun `dragging a rail button vertically reorders the panel list`() {
        val windows =
            listOf(
                ComposeWorkspaceWindow(
                    id = "chat",
                    icon = "chat",
                    title = "Chat",
                    subtitle = "",
                    bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200),
                    visible = true,
                ),
                ComposeWorkspaceWindow(
                    id = "todos",
                    icon = "todos",
                    title = "Todos",
                    subtitle = "",
                    bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200),
                    visible = false,
                ),
            )
        val reorderEvents = mutableListOf<List<String>>()

        composeTestRule.setContent {
            MaterialTheme {
                ComposeRail(
                    windows = windows,
                    onToggleWindow = {},
                    onReorderWindows = { reordered -> reorderEvents += reordered.map { it.id } },
                    onPanelWidthChanged = { _, _ -> },
                    onCloseApplication = {},
                    modalRequester = ComposeModalRequester { },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Drag Chat").performTouchInput {
            down(center)
            moveBy(Offset(x = 0f, y = 100f))
            up()
        }
        composeTestRule.waitForIdle()

        assertTrue(
            reorderEvents.isNotEmpty(),
            "Expected a reorder event after dragging the chat rail button down, but got $reorderEvents",
        )
        assertEquals(
            listOf("todos", "chat"),
            reorderEvents.last(),
            "Dragging chat below todos should move chat to the end of the list",
        )
    }
}
