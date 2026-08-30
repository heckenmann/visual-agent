@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.modal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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
    fun `settings modal renders reusable panel settings content`() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                ComposeModalHost(
                    modal = ComposeSettingsModal(title = "Panel settings") { Text("Settings body") },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Panel settings").assertExists()
        composeTestRule.onNodeWithText("Settings body").assertExists()
        composeTestRule.onNodeWithContentDescription("Close Panel settings").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `escape dismisses settings modal without invoking its content action`() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                ComposeModalHost(
                    modal = ComposeSettingsModal(title = "Panel settings") { Text("Settings body") },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Settings body").performKeyInput { keyDown(Key.Escape) }

        assertTrue(dismissed)
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

    @Test
    fun `requestSettings extension function invokes requester`() {
        var requested: ComposeModal? = null
        val requester = ComposeModalRequester { requested = it }

        requester.requestSettings(ComposeSettingsModal(title = "Panel settings") {})

        assertTrue(requested is ComposeSettingsModal)
    }
}
