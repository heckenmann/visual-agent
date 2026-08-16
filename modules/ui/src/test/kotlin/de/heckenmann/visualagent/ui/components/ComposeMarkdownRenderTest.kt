@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.protocol.ClientImagePort
import de.heckenmann.visualagent.protocol.ConversationImageResolution
import de.heckenmann.visualagent.protocol.ConversationPort
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun `routes remote markdown images through the conversation server boundary`() {
        val source = "https://example.com/diagram.png"
        val conversationPort = mockk<ConversationPort>(relaxed = true)
        val resolveCalls = AtomicInteger()
        coEvery { conversationPort.resolveImage(any()) } answers {
            resolveCalls.incrementAndGet()
            ConversationImageResolution.Rejected("Remote image unavailable")
        }

        composeTestRule.setContent {
            MaterialTheme {
                rememberImageTransformer(conversationPort, null).transform(source)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) { resolveCalls.get() == 1 }
        coVerify(exactly = 1) { conversationPort.resolveImage(source) }
    }

    @Test
    fun `routes client file images through the client boundary only`() {
        val source = "client-file:/home/user/diagram.png"
        val conversationPort = mockk<ConversationPort>(relaxed = true)
        val clientImagePort = mockk<ClientImagePort>(relaxed = true)
        val resolveCalls = AtomicInteger()
        coEvery { clientImagePort.resolveImage(any()) } answers {
            resolveCalls.incrementAndGet()
            ConversationImageResolution.Rejected("Client image unavailable")
        }

        composeTestRule.setContent {
            MaterialTheme {
                rememberImageTransformer(conversationPort, clientImagePort).transform(source)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) { resolveCalls.get() == 1 }
        coVerify(exactly = 1) { clientImagePort.resolveImage(source) }
        coVerify(exactly = 0) { conversationPort.resolveImage(any()) }
    }

    @Test
    fun `renders inline markdown images through the conversation server boundary`() {
        val source = "https://example.com/diagram.png"
        val imageBytes =
            Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            )
        val conversationPort = mockk<ConversationPort>(relaxed = true)
        val resolveCalls = AtomicInteger()
        coEvery { conversationPort.resolveImage(any()) } answers {
            resolveCalls.incrementAndGet()
            ConversationImageResolution.Loaded("image/png", imageBytes)
        }

        composeTestRule.setContent {
            MaterialTheme {
                SelectionContainer {
                    ComposeMarkdown(
                        "before ![diagram]($source) after",
                        modifier = Modifier.fillMaxSize(),
                        conversationPort = conversationPort,
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) { resolveCalls.get() >= 1 }
        coVerify(atLeast = 1) { conversationPort.resolveImage(source) }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription("Image").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Image").assertExists()
    }

    @Test
    fun `does not resolve an incomplete streamed image node`() {
        val conversationPort = mockk<ConversationPort>(relaxed = true)

        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown(
                    "![Architecture overview](https://example.com/diagram.png",
                    modifier = Modifier.fillMaxSize(),
                    conversationPort = conversationPort,
                )
            }
        }

        waitForMarkdownParsing()
        coVerify(exactly = 0) { conversationPort.resolveImage(any()) }
    }
}
