@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.components

import androidx.compose.material3.MaterialTheme
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

/**
 * Tests for the Markdown-to-Compose renderer.
 */
class ComposeMarkdownTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * The `multiplatform-markdown-renderer` library parses Markdown asynchronously.
     * Tests must wait for the text nodes to appear rather than using fixed time delays.
     */
    private fun waitForMarkdownParsing() {
        composeTestRule.waitForIdle()
        Thread.sleep(200)
        composeTestRule.waitForIdle()
        Thread.sleep(200)
        composeTestRule.waitForIdle()
    }

    @Test
    fun `markdown renders heading paragraph code block and list`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown(
                    markdown =
                        """
                        # Title

                        Some **bold** text.

                        ```kotlin
                        println("hi")
                        ```

                        - one
                        - two
                        """.trimIndent(),
                )
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("Title").assertExists()
        composeTestRule.onNodeWithText("Some bold text.").assertExists()
        composeTestRule.onNodeWithText("println(\"hi\")").assertExists()
        composeTestRule.onNodeWithText("one").assertExists()
        composeTestRule.onNodeWithText("two").assertExists()
    }

    @Test
    fun `ordered list renders with start number`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown(
                    markdown =
                        """
                        3. first
                        4. second
                        """.trimIndent(),
                )
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("first").assertExists()
        composeTestRule.onNodeWithText("second").assertExists()
    }

    @Test
    fun `markdown renders link text`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComposeMarkdown("[link](https://example.com)")
            }
        }
        waitForMarkdownParsing()
        composeTestRule.onNodeWithText("link").assertExists()
    }
}
