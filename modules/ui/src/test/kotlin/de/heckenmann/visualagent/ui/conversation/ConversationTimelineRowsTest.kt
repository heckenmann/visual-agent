@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import kotlin.test.assertEquals
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
                    conversationTimeline(
                        items =
                            buildConversationTimeline(
                                history = listOf(Message(role = "sub_agent", content = "## Result", metadata = metadata)),
                                pendingUserMessage = null,
                                streamingContent = "",
                                requestActive = false,
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
    fun `persisted conversation messages retain selectable content`() {
        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn {
                    conversationTimeline(
                        items =
                            buildConversationTimeline(
                                history = listOf(Message(role = "user", content = "Copy me", id = "message-1")),
                                pendingUserMessage = null,
                                streamingContent = "",
                                requestActive = false,
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

        composeTestRule.onNodeWithContentDescription("Message actions").assertExists()
    }

    @Test
    fun `assistant tool children are grouped by typed parent identity and declaration order`() {
        val parent = Message("assistant", "Inspect both files", id = "turn-1", assistantToolTurn = true)
        val laterCall = Message("tool", "second", id = "call-2", parentAssistantTurnId = "turn-1", turnOrder = 1)
        val earlierCall = Message("tool", "first", id = "call-1", parentAssistantTurnId = "turn-1", turnOrder = 0)

        val timeline =
            buildConversationTimeline(
                history = listOf(laterCall, parent, earlierCall),
                pendingUserMessage = null,
                streamingContent = "",
                requestActive = false,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
            )
        val group = timeline.filterIsInstance<ConversationTimelineItem.PersistedGroup>().single().group

        assertEquals(listOf("turn-1", "call-1", "call-2"), group.messages.map { it.message.id })
    }

    @Test
    fun `assistant prose renders above its tool children in declaration order`() {
        val parent = Message("assistant", "Inspect both files", id = "turn-1", assistantToolTurn = true)
        val laterCall = toolMessage("call-2", "file:grep", "turn-1", 1)
        val earlierCall = toolMessage("call-1", "file:read", "turn-1", 0)
        val timeline =
            buildConversationTimeline(
                history = listOf(laterCall, parent, earlierCall),
                pendingUserMessage = null,
                streamingContent = "",
                requestActive = false,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
            )

        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn {
                    conversationTimeline(
                        items = timeline,
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
        val contentBounds = composeTestRule.onNodeWithText("Inspect both files", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val readBounds = composeTestRule.onNodeWithText("file:read").getUnclippedBoundsInRoot()
        val grepBounds = composeTestRule.onNodeWithText("file:grep").getUnclippedBoundsInRoot()
        assert(contentBounds.top < readBounds.top)
        assert(readBounds.top < grepBounds.top)
    }

    @Test
    fun `legacy tool rows without an explicit parent remain standalone`() {
        val timeline =
            buildConversationTimeline(
                history =
                    listOf(
                        Message("assistant", "I will inspect the file", id = "legacy-assistant"),
                        Message("tool", "Old tool result", id = "legacy-tool"),
                    ),
                pendingUserMessage = null,
                streamingContent = "",
                requestActive = false,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
            )

        val assistant = timeline.filterIsInstance<ConversationTimelineItem.MessageEntry>().single()
        val standaloneTool = timeline.filterIsInstance<ConversationTimelineItem.Persisted>().single()

        assertEquals("legacy-assistant", assistant.message.id)
        assertEquals("legacy-tool", standaloneTool.message.id)
    }

    @Test
    fun `tool only assistant turn renders child without placeholder prose`() {
        val parent = Message("assistant", "", id = "turn-empty", assistantToolTurn = true)
        val child =
            Message(
                "tool",
                "Tool search running",
                metadata = """{"type":"tool_call","toolId":"search","status":"running"}""",
                id = "call-running",
                parentAssistantTurnId = "turn-empty",
                turnOrder = 0,
            )

        composeTestRule.setContent {
            MaterialTheme {
                LazyColumn {
                    conversationTimeline(
                        items =
                            buildConversationTimeline(
                                history = listOf(parent, child),
                                pendingUserMessage = null,
                                streamingContent = "",
                                requestActive = false,
                                showOlderHistoryLoading = false,
                                includeInlineComposer = false,
                            ),
                        sending = true,
                        deletingMessageIds = emptySet(),
                        onDeleteMessage = {},
                        onStatusChange = {},
                        onEditMessage = {},
                        sendContent = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("search").assertExists()
        composeTestRule.onNodeWithText("running…").assertExists()
        composeTestRule.onNodeWithText("(No text response. See tool results above.)").assertDoesNotExist()
    }

    private fun toolMessage(
        id: String,
        toolId: String,
        parentId: String,
        order: Int,
    ) = Message(
        "tool",
        "Tool $toolId · ok",
        metadata = """{"type":"tool_call","toolId":"$toolId","status":"ok"}""",
        id = id,
        parentAssistantTurnId = parentId,
        turnOrder = order,
    )
}
