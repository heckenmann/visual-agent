package de.heckenmann.visualagent.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.ui.application.StartupStatus
import org.junit.Rule
import org.junit.Test

/** Verifies that startup failures and progress states are visible in the desktop splash. */
class ComposeStartupSplashTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `progress state shows application name and status`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStartupSplash(status = StartupStatus.startingServer(), onRetry = {})
            }
        }

        composeTestRule.onNodeWithText("Visual Agent").assertExists()
        composeTestRule.onNodeWithContentDescription("Visual Agent").assertExists()
        composeTestRule.onNodeWithText("Starting the local server").assertExists()
    }

    @Test
    fun `failure state shows retry action`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStartupSplash(status = StartupStatus.failed("Remote server unavailable"), onRetry = {})
            }
        }

        composeTestRule.onNodeWithText("Remote server unavailable").assertExists()
        composeTestRule.onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `startup shows local and saved Visual Agent servers while remote connection is unavailable`() {
        val bookmark =
            DesktopServerBookmark(
                id = "0fea1ab9-61b1-4ad3-9a77-c193d1bf6b89",
                name = "Build server",
                visualAgentServerEndpoint = "grpcs://build.example:7443",
            )
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStartupSplash(
                    status = StartupStatus.startingServer(),
                    bookmarks =
                        DesktopServerBookmarkLoadResult.Loaded(
                            DesktopServerBookmarkState(visualAgentServerBookmarks = listOf(bookmark)),
                        ),
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Local").assertExists()
        composeTestRule.onNodeWithText("Build server").assertExists()
        composeTestRule.onNodeWithText("Connection available later", substring = true).assertExists()
    }

    @Test
    fun `add server opens a clearly labelled Visual Agent server form`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStartupSplash(status = StartupStatus.startingServer(), onRetry = {})
            }
        }

        composeTestRule.onNodeWithText("Add server").performClick()

        composeTestRule.onNodeWithText("Add Visual Agent server").assertExists()
        composeTestRule.onNodeWithText("Visual Agent server endpoint").assertExists()
        composeTestRule.onNodeWithText("not an LLM provider endpoint", substring = true).assertExists()
    }

    @Test
    fun `add server remains available with many saved server bookmarks`() {
        val bookmarks =
            (1..5).map { index ->
                DesktopServerBookmark(
                    id = "00000000-0000-4000-8000-00000000000$index",
                    name = "Server $index",
                    visualAgentServerEndpoint = "grpcs://server$index.example:7443",
                )
            }
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStartupSplash(
                    status = StartupStatus.waitingForServerSelection(),
                    bookmarks = DesktopServerBookmarkLoadResult.Loaded(DesktopServerBookmarkState(visualAgentServerBookmarks = bookmarks)),
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Add server").assertIsDisplayed()
    }

    @Test
    fun `local selection does not start a server until the local row is clicked`() {
        var started = false
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStartupSplash(
                    status = StartupStatus.waitingForServerSelection(),
                    onStartLocal = { started = true },
                    onRetry = {},
                )
            }
        }

        composeTestRule.runOnIdle { kotlin.test.assertFalse(started) }
        composeTestRule.onNodeWithContentDescription("Local Visual Agent server").performClick()
        composeTestRule.runOnIdle { kotlin.test.assertTrue(started) }
    }
}
