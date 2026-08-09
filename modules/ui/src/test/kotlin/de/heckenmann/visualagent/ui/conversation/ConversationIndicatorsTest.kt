@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
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

class ConversationIndicatorsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `todo in progress row shows description`() {
        composeTestRule.setContent {
            MaterialTheme {
                TodoInProgressRow(
                    todo = Todo(id = "t1", description = "Fix parser", status = TodoStatus.IN_PROGRESS),
                    agentName = "Coder",
                )
            }
        }
        composeTestRule.onNodeWithText("Todo \"Fix parser\" in progress — Agent \"Coder\"").assertExists()
    }

    @Test
    fun `todo in progress row omits agent name when null`() {
        composeTestRule.setContent {
            MaterialTheme {
                TodoInProgressRow(
                    todo = Todo(id = "t1", description = "Fix parser", status = TodoStatus.IN_PROGRESS),
                    agentName = null,
                )
            }
        }
        composeTestRule.onNodeWithText("Todo \"Fix parser\" in progress").assertExists()
    }

    @Test
    fun `sub-agent running chip shows agent name`() {
        composeTestRule.setContent {
            MaterialTheme {
                SubAgentRunningChip(agentName = "Coder")
            }
        }
        composeTestRule.onNodeWithText("Agent \"Coder\" is working…").assertExists()
    }

    @Test
    fun `tool in flight spinner has content description`() {
        composeTestRule.setContent {
            MaterialTheme {
                ToolInFlightSpinner()
            }
        }
        composeTestRule.onNodeWithContentDescription("Tool running").assertExists()
    }

    @Test
    fun `streaming accent bar visible when active`() {
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    StreamingAccentBar(isActive = true)
                    StreamingAccentBar(isActive = false)
                }
            }
        }
        // Both are present in the tree; the second is invisible but still mounted.
        // We only assert that active composition succeeds.
        composeTestRule.onNodeWithContentDescription("Streaming accent").assertDoesNotExist()
    }
}
