@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class ComposeSubAgentDetailsEditorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `sub agent details editor renders configuration fields`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
        val agent = manager.createAgent("Worker", "coder")
        val toolConfigService = AgentToolConfigService(db)
        val toolRegistry = mockk<ToolRegistry>()
        every { toolRegistry.toolDefinitions() } returns emptyList()
        val catalog = mockk<ProviderCatalogService>()
        every { catalog.enabledProviders() } returns emptyList()

        composeTestRule.setContent {
            MaterialTheme {
                SubAgentDetailsEditor(
                    agent = agent,
                    agentManager = manager,
                    agentToolConfigService = toolConfigService,
                    toolRegistry = toolRegistry,
                    providerCatalogService = catalog,
                    onSaved = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Name").assertExists()
        composeTestRule.onNodeWithText("Role").assertExists()
        composeTestRule.onNodeWithText("Provider").assertExists()
        composeTestRule.onNodeWithText("Model").assertExists()
        composeTestRule.onNodeWithText("Timeout").assertExists()
    }

    @Test
    fun `sub agents panel renders creation controls and empty state`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
        val toolConfigService = AgentToolConfigService(db)
        val toolRegistry = mockk<ToolRegistry>()
        every { toolRegistry.toolDefinitions() } returns emptyList()
        val catalog = mockk<ProviderCatalogService>()
        every { catalog.enabledProviders() } returns emptyList()

        composeTestRule.setContent {
            MaterialTheme {
                SubAgentsPanel(
                    agentManager = manager,
                    agentToolConfigService = toolConfigService,
                    toolRegistry = toolRegistry,
                    providerCatalogService = catalog,
                    modalRequester = ComposeModalRequester { },
                    inFlight = InFlightStateHolder(),
                    toolEventBus = ToolEventBus(),
                )
            }
        }

        composeTestRule.onNodeWithText("Task for selected sub-agent").assertExists()
    }
}
