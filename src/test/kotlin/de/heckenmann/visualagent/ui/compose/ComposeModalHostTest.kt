@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Tests for the internal modal host.
 */
class ComposeModalHostTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `confirmation modal renders message and invokes confirm`() {
        var confirmed = false
        var dismissed = false
        composeTestRule.setContent {
            val modal =
                ComposeConfirmationModal(
                    title = "Delete?",
                    message = "This cannot be undone.",
                    confirmDescription = "Confirm delete",
                    dismissDescription = "Keep",
                    onConfirm = { confirmed = true },
                )
            MaterialTheme {
                ComposeModalHost(
                    modal = modal,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Delete?").assertExists()
        composeTestRule.onNodeWithText("This cannot be undone.").assertExists()
        composeTestRule.onNodeWithContentDescription("Confirm delete").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun `confirmation modal dismiss invokes onDismiss`() {
        var dismissed = false
        composeTestRule.setContent {
            val modal =
                ComposeConfirmationModal(
                    title = "Delete?",
                    message = "This cannot be undone.",
                    confirmDescription = "Confirm delete",
                    dismissDescription = "Keep",
                    onConfirm = {},
                )
            MaterialTheme {
                ComposeModalHost(
                    modal = modal,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Keep").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `info modal renders title and message`() {
        var dismissed = false
        composeTestRule.setContent {
            val modal =
                ComposeInfoModal(
                    title = "Info",
                    message = "Details here.",
                    dismissDescription = "Got it",
                )
            MaterialTheme {
                ComposeModalHost(
                    modal = modal,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Info").assertExists()
        composeTestRule.onNodeWithText("Details here.").assertExists()
        composeTestRule.onNodeWithContentDescription("Got it").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `null modal renders nothing`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeModalHost(modal = null, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("Info").assertDoesNotExist()
    }

    @Test
    fun `content modal renders custom title and content`() {
        var dismissed = false
        composeTestRule.setContent {
            val modal =
                ComposeContentModal(
                    title = "Custom",
                    content = { Text("Custom body") },
                )
            MaterialTheme {
                ComposeModalHost(
                    modal = modal,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Custom").assertExists()
        composeTestRule.onNodeWithText("Custom body").assertExists()
    }

    @Test
    fun `requestInfo extension function invokes requester`() {
        var requested: ComposeModal? = null
        val requester = ComposeModalRequester { requested = it }
        requester.requestInfo(
            ComposeInfoModal(title = "Info", message = "x"),
        )
        assertTrue(requested is ComposeInfoModal)
    }

    @Test
    fun `requestConfirmation extension function invokes requester`() {
        var requested: ComposeModal? = null
        val requester = ComposeModalRequester { requested = it }
        requester.requestConfirmation(
            ComposeConfirmationModal(
                title = "Delete?",
                message = "x",
                confirmDescription = "Yes",
                onConfirm = {},
            ),
        )
        assertTrue(requested is ComposeConfirmationModal)
    }
}
