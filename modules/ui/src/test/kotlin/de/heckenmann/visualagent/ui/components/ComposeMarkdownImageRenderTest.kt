@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

class ComposeMarkdownImageRenderTest {
    @get:Rule
    val composeTestRule: ComposeContentTestRule = ImmediateMarkdownComposeRule()

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

        composeTestRule.waitForIdle()
        coVerify(exactly = 1) { conversationPort.resolveImage(source) }
    }

    @Test
    fun `announces rejected images instead of markdown alt text`() {
        val source = "https://example.com/missing.png"
        val conversationPort = mockk<ConversationPort>(relaxed = true)
        coEvery { conversationPort.resolveImage(source) } returns
            ConversationImageResolution.Rejected("Remote image unavailable")

        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown(
                    "![diagram]($source)",
                    modifier = Modifier.fillMaxSize(),
                    conversationPort = conversationPort,
                )
            }
        }

        val failureDescription = "Image unavailable: Remote image unavailable"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Image unavailable: Remote image unavailable").assertExists()
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

        composeTestRule.waitForIdle()
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

        composeTestRule.waitForIdle()
        coVerify(atLeast = 1) { conversationPort.resolveImage(source) }
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

        composeTestRule.waitForIdle()
        coVerify(exactly = 0) { conversationPort.resolveImage(any()) }
    }
}
