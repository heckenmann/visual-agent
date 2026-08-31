@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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
                composeModalHost(
                    modal = modal,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Delete?").assertExists()
        composeTestRule.onNodeWithText("This cannot be undone.").assertExists()
        composeTestRule.onNodeWithContentDescription("Modal scrollbar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Confirm delete").performClick()
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
                composeModalHost(
                    modal = modal,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Keep").performClick()
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
                composeModalHost(
                    modal = modal,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Info").assertExists()
        composeTestRule.onNodeWithText("Details here.").assertExists()
        composeTestRule.onNodeWithText("Got it").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `null modal renders nothing`() {
        composeTestRule.setContent {
            MaterialTheme {
                composeModalHost(modal = null, onDismiss = {})
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
                composeModalHost(
                    modal = modal,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Custom").assertExists()
        composeTestRule.onNodeWithText("Custom body").assertExists()
    }

    @Test
    fun `content modal header close invokes custom dismissal callback`() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                composeModalHost(
                    modal =
                        ComposeContentModal(
                            title = "Custom",
                            onDismiss = { dismissed = true },
                            content = { Text("Custom body") },
                        ),
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Close").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `settings modal renders reusable panel settings content`() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                composeModalHost(
                    modal = ComposeSettingsModal(title = "Panel settings") { Text("Settings body") },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Panel settings").assertExists()
        composeTestRule.onNodeWithText("Settings body").assertExists()
        composeTestRule.onNodeWithText("Close").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `settings modal keeps content clear of rounded card corners`() {
        composeTestRule.setContent {
            MaterialTheme {
                composeModalHost(
                    modal =
                        ComposeSettingsModal(title = "Panel settings") {
                            Box(modifier = Modifier.size(300.dp, 100.dp).testTag("Settings modal body"))
                        },
                    onDismiss = {},
                )
            }
        }

        val modalBounds = composeTestRule.onNodeWithTag("Internal modal").getUnclippedBoundsInRoot()
        val bodyBounds = composeTestRule.onNodeWithTag("Settings modal body").getUnclippedBoundsInRoot()

        assertTrue(bodyBounds.left - modalBounds.left >= 22.dp)
        assertTrue(modalBounds.right - bodyBounds.right >= 22.dp)
    }

    @Test
    fun `escape dismisses settings modal without invoking its content action`() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                composeModalHost(
                    modal = ComposeSettingsModal(title = "Panel settings") { Text("Settings body") },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Settings body").performKeyInput { keyDown(Key.Escape) }

        assertTrue(dismissed)
    }

    @Test
    fun `short modal sizes itself to its content`() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(800.dp, 1_000.dp)) {
                    composeModalHost(
                        modal = ComposeInfoModal(title = "Info", message = "Short message."),
                        onDismiss = {},
                    )
                }
            }
        }

        val modalBounds = composeTestRule.onNodeWithTag("Internal modal").getUnclippedBoundsInRoot()

        assertTrue(
            modalBounds.bottom - modalBounds.top < 400.dp,
            "Short modal should not occupy the 80% height limit",
        )
    }

    @Test
    fun `overflowing modal shows a scrollbar`() {
        val longMessage = List(100) { "A detailed line of modal content." }.joinToString("\n")
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(800.dp, 1_000.dp)) {
                    composeModalHost(
                        modal = ComposeInfoModal(title = "Info", message = longMessage),
                        onDismiss = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Modal scrollbar").assertExists()
    }

    @Test
    fun `overflowing modal keeps footer action within modal bounds`() {
        val longMessage = List(100) { "A detailed line of modal content." }.joinToString("\n")
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(800.dp, 400.dp)) {
                    composeModalHost(
                        modal =
                            ComposeConfirmationModal(
                                title = "Confirm",
                                message = longMessage,
                                confirmDescription = "Confirm action",
                                onConfirm = {},
                            ),
                        onDismiss = {},
                    )
                }
            }
        }

        val modalBounds = composeTestRule.onNodeWithTag("Internal modal").getUnclippedBoundsInRoot()
        val footerBounds = composeTestRule.onNodeWithText("Confirm action").getUnclippedBoundsInRoot()

        assertTrue(footerBounds.bottom <= modalBounds.bottom)
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
