@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the Markdown-to-Compose renderer.
 */
class ComposeMarkdownTest {
    @get:Rule
    val composeTestRule = createComposeRule()

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
        composeTestRule.onNodeWithText("3.").assertExists()
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
        composeTestRule.onNodeWithText("link").assertExists()
    }
}
