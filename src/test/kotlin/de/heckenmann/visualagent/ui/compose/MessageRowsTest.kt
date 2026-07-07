@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.agent.Message
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

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
        composeTestRule.onNodeWithText("USER").assertExists()
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
}
