@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/**
 * Verifies the compact two-column rendering of a conversational message group.
 */
class ConversationMessageGroupRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `group renders one user identity next to every chronological message`() {
        val group =
            ConversationMessageGroup(
                listOf(
                    persisted("newest", "user"),
                    persisted("oldest", "user"),
                ),
            )

        composeTestRule.setContent {
            MaterialTheme {
                ConversationMessageGroupRow(
                    group = group,
                    sending = false,
                    deletingMessageIds = emptySet(),
                    onDeleteMessage = {},
                    onStatusChange = {},
                    onEditMessage = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("You avatar").assertExists()
        composeTestRule.onNodeWithText("oldest").assertExists()
        composeTestRule.onNodeWithText("newest").assertExists()
    }

    @Test
    fun `assistant group uses a distinct agent avatar`() {
        val group = ConversationMessageGroup(listOf(persisted("response", "assistant")))

        composeTestRule.setContent {
            MaterialTheme {
                ConversationMessageGroupRow(
                    group = group,
                    sending = false,
                    deletingMessageIds = emptySet(),
                    onDeleteMessage = {},
                    onStatusChange = {},
                    onEditMessage = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Assistant avatar").assertExists()
    }

    @Test
    fun `one-line message keeps contextual actions on its content row`() {
        val group = ConversationMessageGroup(listOf(persisted("Short message", "user")))

        composeTestRule.setContent {
            MaterialTheme {
                ConversationMessageGroupRow(
                    group = group,
                    sending = false,
                    deletingMessageIds = emptySet(),
                    onDeleteMessage = {},
                    onStatusChange = {},
                    onEditMessage = {},
                    onRetry = {},
                )
            }
        }

        val messageBounds = composeTestRule.onNodeWithText("Short message").getUnclippedBoundsInRoot()
        val actionBounds = composeTestRule.onNodeWithContentDescription("Message actions").getUnclippedBoundsInRoot()
        assertTrue(actionBounds.top < messageBounds.bottom)
        assertTrue(messageBounds.right <= actionBounds.left)
    }

    @Test
    fun `hover timestamp does not reduce message content width`() {
        var showTimestamp by mutableStateOf(false)
        val message = persisted("A message that must retain its available width", "user")

        composeTestRule.setContent {
            MaterialTheme {
                Row(Modifier.width(320.dp)) {
                    Text(
                        text = message.message.content,
                        modifier = Modifier.weight(1f).testTag("message content"),
                    )
                    conversationMessageActionMenu(
                        message = message.message,
                        canEdit = true,
                        canDelete = true,
                        canRetry = false,
                        onEdit = {},
                        onDelete = {},
                        onRetry = {},
                        onCopied = {},
                        timestamp = 1_000L,
                        showTimestamp = showTimestamp,
                    )
                }
            }
        }

        val boundsBeforeHover = composeTestRule.onNodeWithTag("message content").getUnclippedBoundsInRoot()
        composeTestRule.runOnIdle { showTimestamp = true }
        val boundsDuringHover = composeTestRule.onNodeWithTag("message content").getUnclippedBoundsInRoot()

        assertEquals(boundsBeforeHover.right - boundsBeforeHover.left, boundsDuringHover.right - boundsDuringHover.left)
    }

    @Test
    fun `system boundary renders the author column again for the same author`() {
        val groups =
            groupConsecutiveConversationMessages(
                listOf(
                    persisted("after system", "user"),
                    persisted("system status", "system"),
                    persisted("before system", "user"),
                ),
            ).filter { it.role == "user" }

        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    groups.forEach { group ->
                        ConversationMessageGroupRow(
                            group = group,
                            sending = false,
                            deletingMessageIds = emptySet(),
                            onDeleteMessage = {},
                            onStatusChange = {},
                            onEditMessage = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onAllNodes(hasContentDescription("You avatar")).assertCountEquals(2)
    }

    private fun persisted(
        content: String,
        role: String,
    ): ConversationTimelineItem.Persisted = ConversationTimelineItem.Persisted(Message(role, content, id = content), 0)
}
