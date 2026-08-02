@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.error.ErrorCategory
import de.heckenmann.visualagent.error.UserFacingError
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
                category = ErrorCategory.PROVIDER,
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
                category = ErrorCategory.WORKSPACE,
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
                category = ErrorCategory.CANVAS,
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
                category = ErrorCategory.TOOL,
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
