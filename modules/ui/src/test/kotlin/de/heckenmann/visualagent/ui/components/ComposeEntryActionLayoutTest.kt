package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.Agent
import de.heckenmann.visualagent.protocol.AgentExecutionSnapshot
import de.heckenmann.visualagent.protocol.AgentPort
import de.heckenmann.visualagent.protocol.AgentStatus
import de.heckenmann.visualagent.protocol.LifecycleState
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.ui.application.SubAgentsPanel
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.todo.TodoPanel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Compares populated sub-agent and todo cards, not just their global action rows. */
class ComposeEntryActionLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `sub-agent card actions sit beside text with todo spacing at narrow and regular widths`() {
        val agent =
            Agent(
                "worker",
                "Researcher with a deliberately long descriptive name",
                "Research and verify relevant project sources",
                AgentStatus.IDLE,
            )
        val agents = mockk<AgentPort>(relaxed = true)
        var paused = false
        every { agents.list() } returns listOf(agent)
        every { agents.executionSnapshot() } answers { AgentExecutionSnapshot(false, if (paused) setOf(agent.id) else emptySet()) }
        coEvery { agents.pause(agent.id) } coAnswers {
            paused = true
            AgentExecutionSnapshot(false, setOf(agent.id))
        }
        coEvery { agents.resume(agent.id) } coAnswers {
            paused = false
            AgentExecutionSnapshot(false)
        }
        val todos = mockk<TodoPort>(relaxed = true)
        every { todos.list() } returns listOf(TodoItem("todo", "A comparable todo entry"))
        every { todos.agents() } returns emptyList()
        val activity = mockk<ActivityPort>(relaxed = true)
        val width = mutableStateOf(480.dp)
        val showTodos = mutableStateOf(false)
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(width.value, 400.dp).testTag("panel")) {
                    if (showTodos.value) {
                        TodoPanel(todos, ComposeModalRequester { }, LifecycleState())
                    } else {
                        SubAgentsPanel(agents, mockk(relaxed = true), ComposeModalRequester { }, activity, todos)
                    }
                }
            }
        }

        for (panelWidth in listOf(480.dp, 300.dp)) {
            composeTestRule.runOnIdle {
                width.value = panelWidth
                showTodos.value = false
            }
            val running = actionBounds("Pause sub-agent")
            val name = composeTestRule.onNodeWithText(agent.name).getBoundsInRoot()
            val role = composeTestRule.onNodeWithText(agent.role).getBoundsInRoot()
            assertTrue(name.right + 6.dp <= running.first().left)
            assertTrue(role.right + 6.dp <= running.first().left)
            assertTrue(name.right > name.left)
            val actionCenter = (running.first().top + running.first().bottom) / 2
            val textCenter = (name.top + role.bottom) / 2
            assertTrue(kotlin.math.abs((actionCenter - textCenter).value) <= 1f)

            composeTestRule.onNodeWithContentDescription("Pause sub-agent").performClick()
            val pausedBounds = actionBounds("Resume sub-agent")
            assertEquals(running.map { it.left to it.right }, pausedBounds.map { it.left to it.right })
            composeTestRule.onNodeWithContentDescription("Resume sub-agent").performClick()
            actionBounds("Pause sub-agent")

            val panel = composeTestRule.onNodeWithTag("panel").getBoundsInRoot()
            val agentInset = panel.right - running.last().right
            composeTestRule.runOnIdle { showTodos.value = true }
            composeTestRule.waitUntil {
                composeTestRule.onAllNodesWithContentDescription("Edit todo").fetchSemanticsNodes().size == 1
            }
            val edit = composeTestRule.onNodeWithContentDescription("Edit todo").getBoundsInRoot()
            val start = composeTestRule.onNodeWithContentDescription("Start todo").getBoundsInRoot()
            val delete = composeTestRule.onNodeWithContentDescription("Delete todo").getBoundsInRoot()
            assertEquals(panel.right - delete.right, agentInset)
            assertEquals(start.left - edit.right, running[1].left - running[0].right)
            assertEquals(edit.right - edit.left, running[0].right - running[0].left)
        }
        coVerify(exactly = 2) { agents.pause(agent.id) }
        coVerify(exactly = 2) { agents.resume(agent.id) }
    }

    private fun actionBounds(toggleDescription: String): List<androidx.compose.ui.unit.DpRect> {
        val descriptions = listOf(toggleDescription, "View sub-agent logs", "Configure sub-agent details", "Delete sub-agent")
        val panel = composeTestRule.onNodeWithTag("panel").getBoundsInRoot()
        val bounds = descriptions.map { composeTestRule.onNodeWithContentDescription(it).assertIsDisplayed().getBoundsInRoot() }
        bounds.forEach {
            assertTrue(it.left >= panel.left && it.right <= panel.right)
            assertEquals(bounds.first().top, it.top)
            assertEquals(32.dp, it.right - it.left)
        }
        bounds.zipWithNext().forEach { (left, right) -> assertEquals(2.dp, right.left - left.right) }
        return bounds
    }
}
