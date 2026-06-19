package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.ui.panels.chat.ChatMarkdownRenderer
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.node.Code
import org.commonmark.node.Link
import org.commonmark.node.OrderedList
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.Parser
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatMarkdownRendererTest {
    @Test
    fun `commonmark parser parses numbered tool list with bold inline code names`() {
        val document =
            Parser.builder().build().parse(
                """
                Mir stehen die folgenden Werkzeuge zur Verfügung:

                1.  **`search`**: Um eine Suche durchzuführen.
                2.  **`file_read`**: Um eine Textdatei zu lesen.
                """.trimIndent(),
            )

        val list = assertIs<OrderedList>(document.firstChild.next)
        assertEquals(1, list.startNumber)
        val firstParagraph = list.firstChild.firstChild
        val strong = assertIs<StrongEmphasis>(firstParagraph.firstChild)
        val code = assertIs<Code>(strong.firstChild)
        assertEquals("search", code.literal)
    }

    @Test
    fun `autolink extension parses bare urls as link nodes`() {
        val document =
            Parser
                .builder()
                .extensions(listOf(AutolinkExtension.create()))
                .build()
                .parse("Open https://ollama.com/upgrade for details.")

        val paragraph = document.firstChild
        val link = assertIs<Link>(paragraph.firstChild.next)
        assertEquals("https://ollama.com/upgrade", link.destination)
    }

    @Test
    fun `renderer maps headings lists code emphasis links and line breaks`() =
        FxTestSupport.run {
            val rendered =
                ChatMarkdownRenderer.render(
                    """
                    # Heading

                    Paragraph with **bold**, `code`, and https://example.com.
                    Next line.

                    3. First
                    4. Second

                    - Bullet

                        indented code

                    ```kotlin
                    val answer = 42
                    ```
                    """.trimIndent(),
                )
            val nodes = descendants(rendered)

            assertTrue(nodes.filterIsInstance<TextFlow>().any { it.styleClass.contains("chat-md-heading") })
            assertTrue(nodes.filterIsInstance<Text>().any { it.styleClass.contains("chat-md-bold") })
            assertTrue(nodes.filterIsInstance<Text>().any { it.styleClass.contains("chat-md-inline-code") })
            assertTrue(nodes.filterIsInstance<Text>().any { it.styleClass.contains("chat-md-link") })
            assertTrue(nodes.filterIsInstance<HBox>().any { it.styleClass.contains("chat-md-list-item") })
            assertTrue(nodes.filterIsInstance<Label>().any { it.styleClass.contains("chat-md-code-block") })
        }

    @Test
    fun `empty markdown still renders a paragraph`() =
        FxTestSupport.run {
            val rendered = ChatMarkdownRenderer.render("") as VBox

            assertEquals(1, rendered.children.size)
            assertTrue((rendered.children.single() as TextFlow).styleClass.contains("chat-md-paragraph"))
        }

    private fun descendants(root: Parent): List<javafx.scene.Node> =
        root.childrenUnmodifiable.flatMap { child ->
            listOf(child) + if (child is Parent) descendants(child) else emptyList()
        }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
