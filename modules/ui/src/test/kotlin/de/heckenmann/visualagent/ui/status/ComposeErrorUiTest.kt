@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.status

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ProtocolErrorCategory
import de.heckenmann.visualagent.protocol.UserFacingError
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the error modal and inline error banner.
 */
class ComposeErrorUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `error modal renders summary and detail`() {
        val userError =
            UserFacingError(
                category = ProtocolErrorCategory.PROVIDER,
                summary = "Provider unreachable",
                detail = "Check the base URL.",
                retryable = true,
            )
        composeTestRule.setContent {
            MaterialTheme {
                ComposeModalHost(
                    modal =
                        ComposeErrorModal(
                            userError = userError,
                            onDismiss = {},
                        ),
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Provider unreachable").assertExists()
        composeTestRule.onNodeWithText("Check the base URL.").assertExists()
    }

    @Test
    fun `error modal retry action is shown for retryable errors`() {
        val userError =
            UserFacingError(
                category = ProtocolErrorCategory.WORKSPACE,
                summary = "Import failed",
                detail = "Try again.",
                retryable = true,
            )
        var retried = false
        composeTestRule.setContent {
            MaterialTheme {
                ComposeModalHost(
                    modal =
                        ComposeErrorModal(
                            userError = userError,
                            onDismiss = {},
                            onRetry = { retried = true },
                        ),
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Retry").performClick()

        assertTrue(retried)
    }

    @Test
    fun `error modal copy action invokes callback`() {
        val userError =
            UserFacingError(
                category = ProtocolErrorCategory.CANVAS,
                summary = "Export failed",
                detail = "Could not render.",
                retryable = false,
            )
        var copied = false
        composeTestRule.setContent {
            MaterialTheme {
                ComposeModalHost(
                    modal =
                        ComposeErrorModal(
                            userError = userError,
                            onDismiss = {},
                            onCopyDetails = { copied = true },
                        ),
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Copy error details").performClick()

        assertTrue(copied)
    }

    @Test
    fun `error banner renders summary and detail`() {
        val userError =
            UserFacingError(
                category = ProtocolErrorCategory.TOOL,
                summary = "Tool input invalid",
                detail = "Missing path.",
                retryable = false,
            )
        composeTestRule.setContent {
            MaterialTheme {
                ErrorBanner(userError = userError)
            }
        }

        composeTestRule.onNodeWithText("Tool input invalid").assertExists()
        composeTestRule.onNodeWithText("Missing path.").assertExists()
    }
}
