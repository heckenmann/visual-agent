@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.status

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.heckenmann.visualagent.agent.AgentStatusCallbackAdapter
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoEventBus
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentStatusCallbackEffectTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `busy and idle messages update running agent ids`() {
        val adapter = AgentStatusCallbackAdapter()
        val todoEventBus = TodoEventBus()
        val inFlight = InFlightStateHolder()
        composeTestRule.setContent {
            MaterialTheme {
                RegisterAgentStatusCallback(inFlight, adapter, todoEventBus)
            }
        }
        composeTestRule.waitForIdle()

        adapter.notify("agent-1", "STATUS:BUSY")
        assertEquals(setOf("agent-1"), inFlight.state.value.runningAgentIds)

        adapter.notify("agent-1", "STATUS:IDLE")
        assertEquals(emptySet(), inFlight.state.value.runningAgentIds)
    }

    @Test
    fun `todo in progress update sets current todo`() {
        val adapter = AgentStatusCallbackAdapter()
        val todoEventBus = TodoEventBus()
        val inFlight = InFlightStateHolder()
        composeTestRule.setContent {
            MaterialTheme {
                RegisterAgentStatusCallback(inFlight, adapter, todoEventBus)
            }
        }
        composeTestRule.waitForIdle()

        val todo = Todo(id = "t1", description = "Fix parser", status = TodoStatus.IN_PROGRESS)
        todoEventBus.publish(TodoChange(TodoChangeType.UPDATED, todo = todo))
        composeTestRule.waitForIdle()

        val currentTodo = inFlight.state.value.currentTodoInProgress
        assertEquals("t1", currentTodo?.id)
        assertEquals("Fix parser", currentTodo?.description)
    }

    @Test
    fun `todo completion clears current todo`() {
        val adapter = AgentStatusCallbackAdapter()
        val todoEventBus = TodoEventBus()
        val inFlight = InFlightStateHolder()
        composeTestRule.setContent {
            MaterialTheme {
                RegisterAgentStatusCallback(inFlight, adapter, todoEventBus)
            }
        }
        composeTestRule.waitForIdle()

        val inProgress = Todo(id = "t1", description = "Fix parser", status = TodoStatus.IN_PROGRESS)
        todoEventBus.publish(TodoChange(TodoChangeType.UPDATED, todo = inProgress))
        composeTestRule.waitForIdle()

        val completed = Todo(id = "t1", description = "Fix parser", status = TodoStatus.COMPLETED)
        todoEventBus.publish(TodoChange(TodoChangeType.UPDATED, todo = completed))
        composeTestRule.waitForIdle()

        assertNull(inFlight.state.value.currentTodoInProgress)
    }
}
