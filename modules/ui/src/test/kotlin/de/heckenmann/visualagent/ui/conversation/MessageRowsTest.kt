@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Tests for the assistant/user message row and edit modal.
 */
class MessageRowsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `message row shows user role and invokes copy`() {
        var copied = false
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "user", content = "hello", id = "msg-1"),
                    isStreamingPlaceholder = false,
                    isStreaming = false,
                    canRetry = false,
                    canEdit = false,
                    canDelete = false,
                    isDeleting = false,
                    onCopied = { copied = true },
                    onRetry = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithText("You").assertExists()
        composeTestRule.onNodeWithText("hello").assertExists()
        composeTestRule.onNodeWithContentDescription("Copy user message").performClick()
        assertTrue(copied)
    }

    @Test
    fun `message row shows retry button when allowed`() {
        var retried = false
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "assistant", content = "hi", id = "msg-1"),
                    isStreamingPlaceholder = false,
                    isStreaming = false,
                    canRetry = true,
                    canEdit = false,
                    canDelete = false,
                    isDeleting = false,
                    onCopied = {},
                    onRetry = { retried = true },
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Retry from previous user message").performClick()
        assertTrue(retried)
    }

    @Test
    fun `message row shows edit button when allowed`() {
        var edited = false
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "user", content = "hello", id = "msg-1"),
                    isStreamingPlaceholder = false,
                    isStreaming = false,
                    canRetry = false,
                    canEdit = true,
                    canDelete = false,
                    isDeleting = false,
                    onCopied = {},
                    onRetry = {},
                    onEdit = { edited = true },
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Edit user message").performClick()
        assertTrue(edited)
    }

    @Test
    fun `message row hides content while deleting`() {
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "assistant", content = "hi", id = "msg-1"),
                    isStreamingPlaceholder = false,
                    isStreaming = false,
                    canRetry = false,
                    canEdit = false,
                    canDelete = false,
                    isDeleting = true,
                    onCopied = {},
                    onRetry = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithText("hi").assertDoesNotExist()
    }

    @Test
    fun `message row delete button invokes onDelete`() {
        var deleted = false
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "assistant", content = "hi", id = "msg-1"),
                    isStreamingPlaceholder = false,
                    isStreaming = false,
                    canRetry = false,
                    canEdit = false,
                    canDelete = true,
                    isDeleting = false,
                    onCopied = {},
                    onRetry = {},
                    onEdit = {},
                    onDelete = { deleted = true },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Delete assistant message").performClick()
        assertTrue(deleted)
    }

    @Test
    fun `message row with retry only shows retry and copy buttons`() {
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "assistant", content = "hi", id = "msg-1"),
                    isStreamingPlaceholder = false,
                    isStreaming = false,
                    canRetry = true,
                    canEdit = false,
                    canDelete = false,
                    isDeleting = false,
                    onCopied = {},
                    onRetry = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Copy assistant message").assertExists()
        composeTestRule.onNodeWithContentDescription("Retry from previous user message").assertExists()
        composeTestRule.onNodeWithContentDescription("Edit assistant message").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Delete assistant message").assertDoesNotExist()
    }

    @Test
    fun `streaming placeholder shows thinking text`() {
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "assistant", content = "", id = null),
                    isStreamingPlaceholder = true,
                    isStreaming = false,
                    canRetry = false,
                    canEdit = false,
                    canDelete = false,
                    isDeleting = false,
                    onCopied = {},
                    onRetry = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Thinking…").assertExists()
    }

    @Test
    fun `thinking markup is rendered separately from the assistant answer`() {
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "assistant", content = "<think>**planning**</think>answer", id = "msg-1"),
                    isStreamingPlaceholder = false,
                    isStreaming = true,
                    canRetry = false,
                    canEdit = false,
                    canDelete = false,
                    isDeleting = false,
                    onCopied = {},
                    onRetry = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Thinking…").assertExists()
        composeTestRule.onNodeWithText("answer").assertExists()
        composeTestRule.onNodeWithText("planning").assertExists()
        composeTestRule.onNodeWithContentDescription("Expand thinking").performClick()
        composeTestRule.onNodeWithText("**planning**").assertDoesNotExist()
        composeTestRule.onNodeWithText("<think>**planning**</think>answer").assertDoesNotExist()
    }

    @Test
    fun `persisted thinking markup remains visible after streaming completes`() {
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "assistant", content = "<think>first\n\nsecond</think>answer", id = "msg-2"),
                    isStreamingPlaceholder = false,
                    isStreaming = false,
                    canRetry = false,
                    canEdit = false,
                    canDelete = false,
                    isDeleting = false,
                    onCopied = {},
                    onRetry = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Thinking").assertExists()
        composeTestRule.onNodeWithText("answer").assertExists()
    }

    @Test
    fun `structured reasoning is rendered in the thinking row without markup`() {
        composeTestRule.setContent {
            MaterialTheme {
                MessageRow(
                    message = Message(role = "assistant", content = "answer", reasoning = "planning", id = "msg-reasoning"),
                    isStreamingPlaceholder = false,
                    isStreaming = false,
                    canRetry = false,
                    canEdit = false,
                    canDelete = false,
                    isDeleting = false,
                    onCopied = {},
                    onRetry = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Thinking").assertExists()
        composeTestRule.onNodeWithText("answer").assertExists()
        composeTestRule.onNodeWithText("<think>planning</think>").assertDoesNotExist()
    }

    @Test
    fun `separate thinking blocks are separated by a blank line`() {
        val parsed = parseThinkingMarkup("<think>first</think>answer<think>second</think>")

        assertEquals("first\n\nsecond", parsed.thinking)
        assertEquals("answer", parsed.answer)
    }

    @Test
    fun `renders a validated canvas image attachment`() {
        val canvasImage =
            "data:image/png;base64," +
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="

        composeTestRule.setContent {
            MaterialTheme {
                ConversationImageAttachments(listOf(canvasImage))
            }
        }

        composeTestRule.waitForIdle()
        Thread.sleep(100)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Embedded image 1").assertExists()
    }
}
