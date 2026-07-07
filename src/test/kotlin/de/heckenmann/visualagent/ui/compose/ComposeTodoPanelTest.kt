@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the todo management panel.
 */
class ComposeTodoPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `todo panel renders todos and filter controls`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        manager.todoManager.add("Task one")
        manager.todoManager.add("Task two")

        val requester = ComposeModalRequester { }
        composeTestRule.setContent {
            MaterialTheme {
                TodoPanel(manager, requester)
            }
        }

        composeTestRule.onNodeWithText("Task one").assertExists()
        composeTestRule.onNodeWithText("Task two").assertExists()
        composeTestRule.onNodeWithText("Total 2 · showing 2").assertExists()
    }

    @Test
    fun `todo panel shows completed count label`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        manager.todoManager.add("Pending task")
        manager.todoManager.add("Completed task")
        val completedId =
            manager.todoManager
                .getAll()
                .first { it.description == "Completed task" }
                .id
        manager.todoManager.updateStatus(completedId, de.heckenmann.visualagent.todo.TodoStatus.COMPLETED)

        val requester = ComposeModalRequester { }
        composeTestRule.setContent {
            MaterialTheme {
                TodoPanel(manager, requester)
            }
        }

        composeTestRule.onNodeWithText("Total 2 · showing 2").assertExists()
        composeTestRule.onNodeWithText("Completed task").assertExists()
    }
}
