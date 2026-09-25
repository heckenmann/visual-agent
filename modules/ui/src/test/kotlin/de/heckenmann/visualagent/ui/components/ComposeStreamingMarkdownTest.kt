package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import org.junit.Rule
import org.junit.Test

/** Verifies incremental Markdown rendering for append-only assistant response streams. */
class ComposeStreamingMarkdownTest {
    @get:Rule
    val composeTestRule: ComposeContentTestRule = ImmediateMarkdownComposeRule()

    @Test
    fun `renders Markdown before the stream completes`() {
        var markdown by mutableStateOf("")
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStreamingMarkdown(
                    markdown = markdown,
                    streamKey = "assistant-response",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        updateMarkdown { markdown = "# Heading\n\n" }

        composeTestRule.onNode(hasText("Heading", substring = true), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("# Heading", substring = true), useUnmergedTree = true).assertDoesNotExist()

        updateMarkdown { markdown += "- first item\n" }

        composeTestRule.onNode(hasText("first item", substring = true), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `renders a streamed section boundary as a Markdown block boundary`() {
        var markdown by mutableStateOf("Initial status.")
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStreamingMarkdown(
                    markdown = markdown,
                    streamKey = "tool-follow-up-response",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        updateMarkdown { markdown += "\n\n## Tool result\n\n- final item" }

        composeTestRule.onNode(hasText("Tool result", substring = true), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("## Tool result", substring = true), useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNode(hasText("final item", substring = true), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `renders an open code block as chunks arrive`() {
        var markdown by mutableStateOf("")
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStreamingMarkdown(
                    markdown = markdown,
                    streamKey = "assistant-response",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        updateMarkdown { markdown = "```kotlin\nval first = 1\n" }

        composeTestRule.onNode(hasText("val first = 1", substring = true), useUnmergedTree = true).assertExists()

        updateMarkdown { markdown += "val second = 2\n```" }

        composeTestRule.onNode(hasText("val first = 1", substring = true), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("val second = 2", substring = true), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `does not retain content when the stream key changes`() {
        var markdown by mutableStateOf("First response")
        var streamKey by mutableStateOf("first-response")
        composeTestRule.setContent {
            MaterialTheme {
                ComposeStreamingMarkdown(
                    markdown = markdown,
                    streamKey = streamKey,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeTestRule.waitForIdle()

        updateMarkdown {
            streamKey = "second-response"
            markdown = "## Second response\n"
        }

        composeTestRule.onNode(hasText("Second response", substring = true), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("First response", substring = true), useUnmergedTree = true).assertDoesNotExist()
    }

    private fun updateMarkdown(update: () -> Unit) {
        composeTestRule.runOnIdle(update)
        composeTestRule.waitForIdle()
    }
}
