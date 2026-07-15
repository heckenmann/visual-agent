@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the todo management panel.
 */
class ComposeTodoPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createManager(): AgentManager {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
        return AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
    }

    @Test
    fun `todo panel renders todos and filter controls`() {
        val manager = createManager()
        manager.todoManager.add("Task one")
        manager.todoManager.add("Task two")

        val requester = ComposeModalRequester { }
        composeTestRule.setContent {
            MaterialTheme {
                TodoPanel(manager, requester, manager.todoEventBus)
            }
        }

        composeTestRule.onNodeWithText("Task one").assertExists()
        composeTestRule.onNodeWithText("Task two").assertExists()
        composeTestRule.onNodeWithText("Total 2 · showing 2").assertExists()
    }

    @Test
    fun `todo panel shows completed count label`() {
        val manager = createManager()
        manager.todoManager.add("Pending task")
        manager.todoManager.add("Completed task")
        val completedId =
            manager.todoManager
                .getAll()
                .first { it.description == "Completed task" }
                .id
        manager.todoManager.updateStatus(completedId, TodoStatus.COMPLETED)

        val requester = ComposeModalRequester { }
        composeTestRule.setContent {
            MaterialTheme {
                TodoPanel(manager, requester, manager.todoEventBus)
            }
        }

        composeTestRule.onNodeWithText("Total 2 · showing 2").assertExists()
        composeTestRule.onNodeWithText("Completed task").assertExists()
    }

    @Test
    fun `todo panel reactively updates when todo status changes via event bus`() {
        val manager = createManager()
        val todo = manager.todoManager.add("Reactive task")

        val requester = ComposeModalRequester { }
        composeTestRule.setContent {
            MaterialTheme {
                TodoPanel(manager, requester, manager.todoEventBus)
            }
        }

        composeTestRule.onNodeWithText("Reactive task").assertExists()
        composeTestRule.onNodeWithText("Pending").assertExists()

        manager.todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Reactive task").assertExists()
        composeTestRule.onNodeWithText("Completed").assertExists()
    }

    @Test
    fun `todo panel shows cancelled status with label`() {
        val manager = createManager()
        val todo = manager.todoManager.add("Cancel me")

        val requester = ComposeModalRequester { }
        composeTestRule.setContent {
            MaterialTheme {
                TodoPanel(manager, requester, manager.todoEventBus)
            }
        }

        manager.todoManager.updateStatus(todo.id, TodoStatus.CANCELLED)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Cancel me").assertExists()
        composeTestRule.onNodeWithText("Cancelled").assertExists()
    }
}
