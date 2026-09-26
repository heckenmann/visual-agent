@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/** Verifies assistant message panels remain transparent in both conversation row layouts. */
class ConversationAgentTransparencyTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `assistant group background is transparent while user group remains tinted`() {
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    ConversationMessageGroupRow(
                        group = group("question", "user"),
                        sending = false,
                        deletingMessageIds = emptySet(),
                        onDeleteMessage = {},
                        onStatusChange = {},
                        onEditMessage = {},
                        onRetry = {},
                        modifier = Modifier.width(360.dp).testTag("user-group"),
                    )
                    ConversationMessageGroupRow(
                        group = group("answer", "assistant"),
                        sending = false,
                        deletingMessageIds = emptySet(),
                        onDeleteMessage = {},
                        onStatusChange = {},
                        onEditMessage = {},
                        onRetry = {},
                        modifier = Modifier.width(360.dp).testTag("assistant-group"),
                    )
                }
            }
        }

        val userPixel = bottomCenterPixel("user-group")
        val assistantPixel = bottomCenterPixel("assistant-group")
        assertTrue(userPixel.alpha > 0f)
        assertEquals(0f, assistantPixel.alpha)
    }

    @Test
    fun `generic assistant message background is transparent while user message remains tinted`() {
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    genericMessage("question", "user", "user-row")
                    genericMessage("answer", "assistant", "assistant-row")
                }
            }
        }

        val userPixel = bottomCenterPixel("user-row")
        val assistantPixel = bottomCenterPixel("assistant-row")
        assertTrue(userPixel.alpha > 0f)
        assertEquals(0f, assistantPixel.alpha)
    }

    private fun bottomCenterPixel(tag: String) =
        composeTestRule
            .onNodeWithTag(tag)
            .captureToImage()
            .toPixelMap()
            .let { pixels -> pixels[pixels.width / 2, pixels.height - 2] }

    @androidx.compose.runtime.Composable
    private fun genericMessage(
        content: String,
        role: String,
        tag: String,
    ) {
        MessageRow(
            message = Message(role, content, id = content),
            isStreamingPlaceholder = false,
            isStreaming = false,
            canRetry = false,
            canEdit = false,
            canDelete = false,
            isDeleting = false,
            onRetry = {},
            onEdit = {},
            onDelete = {},
            modifier = Modifier.width(360.dp).testTag(tag),
        )
    }

    private fun group(
        content: String,
        role: String,
    ) = ConversationMessageGroup(
        listOf(ConversationTimelineItem.Persisted(Message(role, content, id = content), 0)),
    )
}
