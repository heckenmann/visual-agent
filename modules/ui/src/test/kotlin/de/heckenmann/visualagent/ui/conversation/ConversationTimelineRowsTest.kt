@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/**
 * Verifies role-specific rows rendered through the complete conversation timeline.
 */
class ConversationTimelineRowsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `sub-agent history uses its dedicated row instead of an assistant message row`() {
        val metadata =
            buildJsonObject {
                put("type", "sub_agent")
                put("agentId", "agent-1")
                put("agentName", "Researcher")
                put("todoId", "todo-1")
                put("success", true)
            }.toString()

        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn {
                    ConversationTimeline(
                        items =
                            buildConversationTimeline(
                                history = listOf(Message(role = "sub_agent", content = "## Result", metadata = metadata)),
                                pendingUserMessage = null,
                                streamingContent = "",
                                showWaitingIndicator = false,
                                showOlderHistoryLoading = false,
                                includeInlineComposer = false,
                            ),
                        sending = false,
                        deletingMessageIds = emptySet(),
                        onDeleteMessage = {},
                        onStatusChange = {},
                        onEditMessage = {},
                        sendContent = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Agent \"Researcher\" completed a todo").assertExists()
        composeTestRule.onNodeWithText("Assistant").assertDoesNotExist()
    }

    @Test
    fun `incomplete sub-agent metadata uses the generic message row`() {
        val metadata = parseSubAgentMetadata(null)

        assertFalse(shouldUseSubAgentSummary(metadata))
    }

    @Test
    fun `persisted conversation messages retain their copy action`() {
        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn {
                    ConversationTimeline(
                        items =
                            buildConversationTimeline(
                                history = listOf(Message(role = "user", content = "Copy me", id = "message-1")),
                                pendingUserMessage = null,
                                streamingContent = "",
                                showWaitingIndicator = false,
                                showOlderHistoryLoading = false,
                                includeInlineComposer = false,
                            ),
                        sending = false,
                        deletingMessageIds = emptySet(),
                        onDeleteMessage = {},
                        onStatusChange = {},
                        onEditMessage = {},
                        sendContent = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Copy user message").assertExists()
    }
}
