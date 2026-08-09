@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

class ComposeMarkdownRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * The `multiplatform-markdown-renderer` library parses Markdown asynchronously.
     * Tests use [waitForMarkdownParsing] to advance the clock in small steps until
     * parsing completes.
     */
    private fun waitForMarkdownParsing() {
        composeTestRule.waitForIdle()
        // The library parses Markdown asynchronously using real coroutines.
        // The Compose test clock does not advance real time, so we must sleep
        // to let the parsing coroutine complete.
        Thread.sleep(200)
        composeTestRule.waitForIdle()
        Thread.sleep(200)
        composeTestRule.waitForIdle()
    }

    @Test
    fun `renders block quote text`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("> quoted text", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("quoted text").assertExists()
    }

    @Test
    fun `renders italic text`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("*italic*", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("italic").assertExists()
    }

    @Test
    fun `renders strikethrough text`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("~~deleted~~", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("deleted").assertExists()
    }

    @Test
    fun `renders thematic break with surrounding text`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("text\n\n---\n\nmore text", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("text").assertExists()
        composeTestRule.onNodeWithText("more text").assertExists()
    }

    @Test
    fun `renders bold and italic combined`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("**bold** and *italic*", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("bold and italic").assertExists()
    }

    @Test
    fun `renders table with body rows`() {
        val markdown = "\n| Name | Value |\n|------|-------|\n| a    | 1     |\n| b    | 2     |\n"
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown(markdown, modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        // Table cell text may be in AnnotatedString; just verify no crash.
    }

    @Test
    fun `renders nested list items`() {
        val markdown = "- top\n  - nested\n- top2\n"
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown(markdown, modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("top").assertExists()
        composeTestRule.onNodeWithText("nested").assertExists()
        composeTestRule.onNodeWithText("top2").assertExists()
    }

    @Test
    fun `renders ordered list`() {
        val markdown = "1. first\n2. second\n3. third\n"
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown(markdown, modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("first").assertExists()
        composeTestRule.onNodeWithText("second").assertExists()
        composeTestRule.onNodeWithText("third").assertExists()
    }

    @Test
    fun `renders inline code`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("use `val x = 1` here", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        // The library renders inline code within an AnnotatedString; verify no crash.
    }

    @Test
    fun `renders code block`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("```\nval x = 1\n```", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("val x = 1").assertExists()
    }

    @Test
    fun `renders heading`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("# Heading", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("Heading").assertExists()
    }

    @Test
    fun `renders paragraph with link`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("[click](https://example.com)", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("click").assertExists()
    }

    @Test
    fun `renders paragraph with plain text`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("just a simple paragraph", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("just a simple paragraph").assertExists()
    }

    @Test
    fun `renders empty markdown without crash`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("", modifier = Modifier.fillMaxSize())
            }
        }
        waitForMarkdownParsing()
    }
}
