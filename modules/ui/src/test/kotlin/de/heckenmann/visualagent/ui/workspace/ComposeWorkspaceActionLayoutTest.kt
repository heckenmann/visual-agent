package de.heckenmann.visualagent.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.AgentExecutionSnapshot
import de.heckenmann.visualagent.protocol.LifecycleState
import de.heckenmann.visualagent.ui.application.ComposePanelServices
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Checks action alignment through the real workspace panel headers and scrollbar hosts. */
class ComposeWorkspaceActionLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `todo and sub-agent actions keep equal panel-relative bounds when resized`() {
        val services = mockk<ComposePanelServices>(relaxed = true)
        every { services.agents.list() } returns emptyList()
        every { services.agents.executionSnapshot() } returns AgentExecutionSnapshot(false)
        every { services.todos.list() } returns emptyList()
        every { services.todos.agents() } returns emptyList()
        every { services.lifecycle } returns LifecycleState()
        val windows = mutableStateOf(panels(420))
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(1_200.dp, 600.dp)) {
                    ComposeSplitWorkspace(
                        windows = windows.value,
                        panelServices = services,
                        onToggleWindow = {},
                        onReorderWindows = {},
                        onResizeWindow = { _, _ -> },
                        minPanelWidth = ComposeWorkspaceWindowBounds.MIN_WIDTH,
                        viewport = ComposeWorkspaceViewport(1_200, 600),
                    )
                }
            }
        }
        for (width in listOf(420, ComposeWorkspaceWindowBounds.MIN_WIDTH)) {
            composeTestRule.runOnIdle { windows.value = panels(width) }
            val todo = composeTestRule.onNodeWithTag("workspace-panel-content-todos").getUnclippedBoundsInRoot()
            val agent = composeTestRule.onNodeWithTag("workspace-panel-content-agents").getUnclippedBoundsInRoot()
            val addTodo = composeTestRule.onNodeWithContentDescription("Add todo").getUnclippedBoundsInRoot()
            val addAgent = composeTestRule.onNodeWithContentDescription("Create sub-agent").getUnclippedBoundsInRoot()
            val stop = composeTestRule.onNodeWithContentDescription("Stop all todos").getUnclippedBoundsInRoot()
            val pause = composeTestRule.onNodeWithContentDescription("Pause all sub-agents").getUnclippedBoundsInRoot()
            assertEquals(todo.right - addTodo.right, agent.right - addAgent.right)
            assertEquals(addTodo.top - todo.top, addAgent.top - agent.top)
            assertEquals(addTodo.right - addTodo.left, addAgent.right - addAgent.left)
            assertEquals(addTodo.bottom - addTodo.top, addAgent.bottom - addAgent.top)
            assertEquals(addTodo.left - stop.right, addAgent.left - pause.right)
            assertEquals(stop.top - todo.top, pause.top - agent.top)
        }
    }

    private fun panels(width: Int): List<ComposeWorkspaceWindow> =
        listOf(
            ComposeWorkspaceWindow(
                id = "todos",
                icon = "todos",
                title = "Todos",
                subtitle = "DB-backed task management",
                bounds = ComposeWorkspaceWindowBounds(0, 0, width, 600),
            ),
            ComposeWorkspaceWindow(
                id = "agents",
                icon = "agents",
                title = "Subagents",
                subtitle = "Worker creation and live job counts",
                bounds = ComposeWorkspaceWindowBounds(0, 0, width, 600),
            ),
        )
}
