package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.AgentExecutionSnapshot
import de.heckenmann.visualagent.protocol.AgentPort
import de.heckenmann.visualagent.protocol.LifecycleState
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.ui.application.SubAgentsPanel
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
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

/** Verifies panel action geometry and callbacks at regular and narrow widths. */
class ComposePanelActionLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `sub-agent actions stay adjacent and trailing across execution changes and resizing`() {
        var paused = false
        val agents = mockk<AgentPort>(relaxed = true)
        every { agents.list() } returns emptyList()
        every { agents.executionSnapshot() } answers { AgentExecutionSnapshot(paused) }
        every { agents.addExecutionListener(any()) } returns AutoCloseable { }
        coEvery { agents.pauseAll() } coAnswers {
            paused = true
            AgentExecutionSnapshot(true)
        }
        coEvery { agents.resumeAll() } coAnswers {
            paused = false
            AgentExecutionSnapshot(false)
        }
        val todos = todoPort()
        val activity = mockk<ActivityPort>(relaxed = true)
        every { activity.addToolListener(any()) } returns AutoCloseable { }
        every { activity.addAgentListener(any()) } returns AutoCloseable { }
        val width = mutableStateOf(320.dp)
        var modal: Any? = null
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(width.value, 240.dp).testTag("panel")) {
                    SubAgentsPanel(
                        agents,
                        mockk<ProviderPort>(relaxed = true),
                        ComposeModalRequester { modal = it },
                        activity,
                        todos,
                    )
                }
            }
        }

        val running = subAgentActionBounds("Pause all sub-agents", "Sub-agents running")
        composeTestRule.onNodeWithContentDescription("Pause all sub-agents").performClick()
        composeTestRule.waitForIdle()
        assertEquals(running, subAgentActionBounds("Resume all sub-agents", "All sub-agents paused"))

        composeTestRule.runOnIdle { width.value = 120.dp }
        composeTestRule.waitForIdle()
        val narrowPaused = subAgentActionBounds("Resume all sub-agents", "All sub-agents paused")
        composeTestRule.onNodeWithContentDescription("Resume all sub-agents").performClick()
        composeTestRule.waitForIdle()
        assertEquals(narrowPaused, subAgentActionBounds("Pause all sub-agents", "Sub-agents running"))

        composeTestRule.onNodeWithContentDescription("Create sub-agent").performClick()
        assertEquals("Create sub-agent", (modal as ComposeContentModal).title)
        coVerify(exactly = 1) { agents.pauseAll() }
        coVerify(exactly = 1) { agents.resumeAll() }
    }

    @Test
    fun `todo actions keep their existing spacing order and trailing inset`() {
        val width = mutableStateOf(320.dp)
        val todos = todoPort()
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(width.value, 240.dp).testTag("panel")) {
                    TodoPanel(todos, ComposeModalRequester { }, LifecycleState())
                }
            }
        }

        assertTodoActionBounds()
        composeTestRule.runOnIdle { width.value = 120.dp }
        composeTestRule.waitForIdle()
        assertTodoActionBounds()
    }

    private fun subAgentActionBounds(
        executionAction: String,
        status: String,
    ): List<androidx.compose.ui.unit.DpRect> {
        val toggle = composeTestRule.onNodeWithContentDescription(executionAction).getBoundsInRoot()
        val add = composeTestRule.onNodeWithContentDescription("Create sub-agent").getBoundsInRoot()
        val panel = composeTestRule.onNodeWithTag("panel").getBoundsInRoot()
        val executionStatus = composeTestRule.onNodeWithText(status).getBoundsInRoot()
        assertEquals(panel.right, add.right)
        assertEquals(toggle.right, add.left)
        assertEquals(toggle.top, add.top)
        assertEquals(32.dp, toggle.right - toggle.left)
        assertEquals(toggle.right - toggle.left, add.right - add.left)
        assertEquals(toggle.bottom - toggle.top, add.bottom - add.top)
        assertTrue(executionStatus.top >= add.bottom)
        return listOf(toggle, add)
    }

    private fun assertTodoActionBounds() {
        composeTestRule.waitForIdle()
        val start = composeTestRule.onNodeWithContentDescription("Start all todos").getBoundsInRoot()
        val stop = composeTestRule.onNodeWithContentDescription("Stop all todos").getBoundsInRoot()
        val add = composeTestRule.onNodeWithContentDescription("Add todo").getBoundsInRoot()
        val panel = composeTestRule.onNodeWithTag("panel").getBoundsInRoot()
        assertEquals(start.right, stop.left)
        assertEquals(stop.right, add.left)
        assertEquals(panel.right - 8.dp, add.right)
        assertEquals(start.top, stop.top)
        assertEquals(stop.top, add.top)
        assertEquals(32.dp, add.right - add.left)
    }

    private fun todoPort(): TodoPort {
        val port = mockk<TodoPort>(relaxed = true)
        every { port.list() } returns emptyList()
        every { port.agents() } returns emptyList()
        every { port.addListener(any()) } returns AutoCloseable { }
        every { port.addProgressListener(any()) } returns AutoCloseable { }
        return port
    }
}
