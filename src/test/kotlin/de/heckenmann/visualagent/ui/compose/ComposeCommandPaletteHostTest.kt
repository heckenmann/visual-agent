@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ComposeCommandPaletteHostTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `command palette renders commands and filters by query`() {
        var invoked = false
        val commands =
            listOf(
                ComposeCommand("chat", "Open Chat", "Show chat panel") { invoked = true },
                ComposeCommand("settings", "Open Settings", "Show settings panel") {},
            )

        composeTestRule.setContent {
            MaterialTheme {
                ComposeCommandPaletteHost(visible = true, commands = commands, onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText("Command Palette").assertExists()
        composeTestRule.onNodeWithText("Open Chat").assertExists()
        composeTestRule.onNodeWithText("Search commands").performTextInput("sett")
        composeTestRule.onNodeWithText("Open Settings").assertExists()
        composeTestRule.onNodeWithText("Open Chat").assertDoesNotExist()
    }

    @Test
    fun `command palette shows no matching message for unknown query`() {
        val commands = listOf(ComposeCommand("chat", "Open Chat", "Show chat panel") {})

        composeTestRule.setContent {
            MaterialTheme {
                ComposeCommandPaletteHost(visible = true, commands = commands, onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText("Search commands").performTextInput("xyz")
        composeTestRule.onNodeWithText("No matching commands").assertExists()
    }
}

class ComposeFilterCommandsTest {
    @Test
    fun `filter commands returns all when query is blank`() {
        val commands = listOf(ComposeCommand("a", "A", "desc") {}, ComposeCommand("b", "B", "desc") {})
        assertEquals(2, filterCommands(commands, "").size)
    }

    @Test
    fun `filter commands matches lowercase query`() {
        val commands = listOf(ComposeCommand("chat", "Open Chat", "Show chat") {})
        assertEquals(1, filterCommands(commands, "CHAT").size)
    }
}
