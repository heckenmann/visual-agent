package de.heckenmann.visualagent.ui.agents

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.protocol.Agent
import de.heckenmann.visualagent.protocol.AgentConfig
import de.heckenmann.visualagent.protocol.AgentPort
import de.heckenmann.visualagent.protocol.AgentStatus
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.ToolDefinition
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/** Verifies sub-agent configuration editing through protocol ports. */
class ComposeSubAgentDetailsEditorProtocolTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `editor renders configuration and saves selected tools`() {
        val agent = Agent("agent", "Worker", "Write code", AgentStatus.IDLE, config = AgentConfig(timeout = 30))
        val agents = mockk<AgentPort>(relaxed = true)
        every { agents.toolsFor(agent.id) } returns setOf("terminal")
        every { agents.toolDefinitions() } returns
            listOf(ToolDefinition("terminal", "Run commands"), ToolDefinition("files", "Read files"))
        every { agents.update(agent.id, any(), any(), any()) } returns agent
        val providers = mockk<ProviderPort>(relaxed = true)
        every { providers.enabledProviders() } returns
            listOf(ProviderProfile("ollama", "Ollama", ProviderAdapter.OLLAMA, "http://localhost"))
        every { providers.selectableModels("ollama") } returns listOf(ProviderModel("llama", "Llama"))

        composeTestRule.setContent {
            MaterialTheme {
                SubAgentDetailsEditor(agent, agents, providers) { }
            }
        }

        composeTestRule.onNodeWithText("Name").assertExists()
        composeTestRule.onNodeWithText("Role").assertExists()
        composeTestRule.onNodeWithText("Timeout").assertExists()
        composeTestRule.onNodeWithText("terminal").assertExists()
        composeTestRule.onNodeWithText("files").assertExists()
        composeTestRule.onNodeWithText("Save changes").assertExists()
    }
}
