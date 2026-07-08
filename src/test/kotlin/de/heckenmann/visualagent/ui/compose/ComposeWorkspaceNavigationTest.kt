@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for workspace rail icon mapping, header rendering, and panel status helpers.
 */
class ComposeWorkspaceNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `railIcon maps known panel ids to correct icons`() {
        val chat = testWindow("chat").railIcon()
        val todos = testWindow("todos").railIcon()
        val files = testWindow("files").railIcon()
        val agents = testWindow("agents").railIcon()
        val settings = testWindow("settings").railIcon()
        val canvas = testWindow("canvas").railIcon()
        val unknown = testWindow("other").railIcon()

        assertEquals(Icons.AutoMirrored.Filled.Chat, chat)
        assertEquals(Icons.Filled.CheckCircle, todos)
        assertEquals(Icons.Filled.Folder, files)
        assertEquals(Icons.Filled.Group, agents)
        assertEquals(Icons.Filled.Settings, settings)
        assertEquals(Icons.Filled.Brush, canvas)
        assertEquals(Icons.Filled.Description, unknown)
    }

    @Test
    fun `workspace header renders provider model and beans chips`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeWorkspaceHeader(
                    providerName = "ollama",
                    modelName = "llava",
                    beanDefinitionCount = 123,
                    inFlight = InFlightState(),
                    onStopAll = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Provider ollama").assertExists()
        composeTestRule.onNodeWithText("Model llava").assertExists()
        composeTestRule.onNodeWithText("Beans 123").assertExists()
    }

    @Test
    fun `compose rail renders toggle buttons for panels`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeRail(
                    windows = listOf(testWindow("chat", visible = true), testWindow("todos", visible = false)),
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
    }

    @Test
    fun `compose rail close button invokes callback`() {
        var closed = false
        composeTestRule.setContent {
            MaterialTheme {
                ComposeRail(
                    windows = emptyList(),
                    onToggleWindow = {},
                    onMoveWindowEarlier = {},
                    onMoveWindowLater = {},
                    onPanelWidthChanged = { _, _ -> },
                    onCloseApplication = { closed = true },
                    modalRequester = ComposeModalRequester { },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Close application").performClick()
        assertTrue(closed)
    }

    @Test
    fun `panel status renders text`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelStatus("Ready")
            }
        }
        composeTestRule.onNodeWithText("Ready").assertExists()
    }

    private fun testWindow(
        id: String,
        visible: Boolean = true,
    ): ComposeWorkspaceWindow =
        ComposeWorkspaceWindow(
            id = id,
            icon = id.take(1),
            title = id.replaceFirstChar(Char::titlecase),
            subtitle = id,
            bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 300),
            visible = visible,
        )
}
