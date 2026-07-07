@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

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
                    onMoveWindowEarlier = {},
                    onMoveWindowLater = {},
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
}
