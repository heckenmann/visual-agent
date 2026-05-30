package de.heckenmann.visualagent.ui.panels

import org.commonmark.node.Code
import org.commonmark.node.OrderedList
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.Parser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChatMarkdownRendererTest {
    @Test
    fun `commonmark parser parses numbered tool list with bold inline code names`() {
        val document = Parser.builder().build().parse(
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
}
