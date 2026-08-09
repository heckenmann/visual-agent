@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
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
    fun `clicking a sortable rail item toggles its panel`() {
        val windows = listOf(testWindow("chat", "Chat"))
        var toggledPanelId: String? = null

        composeTestRule.setContent {
            MaterialTheme {
                ComposeRail(
                    windows = windows,
                    onToggleWindow = { toggledPanelId = it },
                    onReorderWindows = {},
                    onPanelWidthChanged = { _, _ -> },
                    onCloseApplication = {},
                    modalRequester = ComposeModalRequester { },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Toggle Chat").performClick()

        assertEquals("chat", toggledPanelId)
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

    @Test
    fun `rail toggle button immediately switches between compact and labelled modes`() {
        val windows = listOf(testWindow("chat", "Chat"), testWindow("files", "A very long panel name"))

        composeTestRule.setContent {
            var showLabels by remember { mutableStateOf(false) }
            MaterialTheme {
                ComposeRail(
                    windows = windows,
                    showPanelLabels = showLabels,
                    onTogglePanelLabels = { showLabels = !showLabels },
                    onToggleWindow = {},
                    onReorderWindows = {},
                    onPanelWidthChanged = { _, _ -> },
                    onCloseApplication = {},
                    modalRequester = ComposeModalRequester { },
                )
            }
        }

        composeTestRule.onNodeWithText("Chat").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Show panel labels").performClick()
        composeTestRule.onNodeWithText("Chat").assertExists()
        composeTestRule.onNodeWithText("A very long panel name").assertExists()
        composeTestRule.onNodeWithContentDescription("Hide panel labels").assertExists()
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
