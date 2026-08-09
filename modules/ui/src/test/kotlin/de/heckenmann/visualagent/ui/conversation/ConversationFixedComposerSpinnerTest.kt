package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.config.ConversationInputPlacement
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies that a pinned composer cannot cover the active conversation indicator. */
class ConversationFixedComposerSpinnerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `active indicator remains above the fixed composer`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val config = AppConfigBean(db).also { it.conversationInputPlacement = ConversationInputPlacement.FIXED }
        val toolEventBus = ToolEventBus()
        val inFlight = InFlightStateHolder().also { it.markStreamStart("request-1") }
        val manager =
            AgentManager(
                db,
                mockk<LLMProvider>(relaxed = true),
                AgentToolConfigService(db),
                toolEventBus,
                TodoEventBus(),
                config,
            )

        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(480.dp).height(600.dp)) {
                    ConversationPanel(
                        agentManager = manager,
                        modalRequester = ComposeModalRequester { },
                        inFlight = inFlight,
                        toolEventBus = toolEventBus,
                        todoEventBus = TodoEventBus(),
                        config = config,
                    )
                }
            }
        }

        val indicator = composeTestRule.onNodeWithText("Thinking")
        val composer = composeTestRule.onNodeWithText("Type a message…")
        indicator.assertIsDisplayed()
        composer.assertIsDisplayed()
        assertTrue(
            indicator.fetchSemanticsNode().boundsInRoot.bottom <= composer.fetchSemanticsNode().boundsInRoot.top,
            "the active indicator must remain above the fixed composer",
        )
    }
}
