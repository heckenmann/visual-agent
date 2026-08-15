package de.heckenmann.visualagent.ui.agents

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.Agent
import de.heckenmann.visualagent.protocol.AgentExecutionSnapshot
import de.heckenmann.visualagent.protocol.AgentPort
import de.heckenmann.visualagent.protocol.AgentStatus
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.ui.application.SubAgentsPanel
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/** Verifies sub-agent controls using only protocol-owned agent data. */
class ComposeSubAgentsPanelProtocolTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel renders agent details and forwards pause action`() {
        val agent = Agent("agent-1", "Researcher", "Find relevant sources", AgentStatus.IDLE)
        val agents = mockk<AgentPort>(relaxed = true)
        every { agents.list() } returns listOf(agent)
        every { agents.executionSnapshot() } returns AgentExecutionSnapshot(false)
        every { agents.activeJobCount(agent.id) } returns 2
        every { agents.addExecutionListener(any()) } returns AutoCloseable { }
        every { agents.addChangeListener(any()) } returns AutoCloseable { }
        coEvery { agents.pause(agent.id) } returns AgentExecutionSnapshot(false, setOf(agent.id))
        val todos = mockk<TodoPort>(relaxed = true)
        every { todos.addListener(any()) } returns AutoCloseable { }
        val activity = mockk<ActivityPort>(relaxed = true)
        every { activity.addToolListener(any()) } returns AutoCloseable { }
        every { activity.addAgentListener(any()) } returns AutoCloseable { }

        composeTestRule.setContent {
            MaterialTheme {
                SubAgentsPanel(
                    agentPort = agents,
                    providerPort = mockk<ProviderPort>(relaxed = true),
                    modalRequester = ComposeModalRequester { },
                    activityPort = activity,
                    todoPort = todos,
                )
            }
        }

        composeTestRule.onNodeWithText("Researcher").assertExists()
        composeTestRule.onNodeWithText("Idle · active jobs 2").assertExists()
        composeTestRule.onNodeWithText("Find relevant sources").assertExists()
        composeTestRule.onNodeWithContentDescription("Pause sub-agent").performClick()
        composeTestRule.waitForIdle()
        coVerify { agents.pause(agent.id) }
    }

    @Test
    fun `log summary includes recent context and missing values`() {
        val summary = subAgentLogSummary(Agent("id", "name", "role", AgentStatus.BUSY), 3)
        kotlin.test.assertTrue(summary.contains("Active jobs: 3"))
        kotlin.test.assertTrue(summary.contains("Current task: None"))
        kotlin.test.assertTrue(summary.contains("No recent chat history."))
    }
}
