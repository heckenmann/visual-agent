@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class ComposeConversationPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `conversation panel renders empty state and input area`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

        composeTestRule.setContent {
            MaterialTheme {
                ConversationPanel(
                    agentManager = manager,
                    modalRequester = ComposeModalRequester { },
                    inFlight = InFlightStateHolder(),
                    toolEventBus = ToolEventBus(),
                )
            }
        }

        composeTestRule.onNodeWithText("No conversation yet").assertExists()
        composeTestRule.onNodeWithText("Ready").assertExists()
    }

    @Test
    fun `conversation panel renders existing history`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
        manager.appendSystemMessage("system context")

        composeTestRule.setContent {
            MaterialTheme {
                ConversationPanel(
                    agentManager = manager,
                    modalRequester = ComposeModalRequester { },
                    inFlight = InFlightStateHolder(),
                    toolEventBus = ToolEventBus(),
                )
            }
        }

        composeTestRule.onNodeWithText("system context").assertExists()
    }
}
