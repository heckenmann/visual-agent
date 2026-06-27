package de.heckenmann.visualagent.ui.compose

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComposeMarkdownParserTest {
    @Test
    fun `parser keeps headings paragraphs and inline styles structured`() {
        val blocks = ComposeMarkdownParser.parse("# Title\n\nHello **bold** `code` [link](https://example.com)")

        val heading = assertIs<ComposeMarkdownBlock.Heading>(blocks[0])
        val paragraph = assertIs<ComposeMarkdownBlock.Paragraph>(blocks[1])

        assertEquals("Title", heading.inlines.single().text)
        assertTrue(paragraph.inlines.any { it.text == "bold" && it.bold })
        assertTrue(paragraph.inlines.any { it.text == "code" && it.code })
        assertTrue(paragraph.inlines.any { it.text == "link" && it.linkDestination == "https://example.com" })
    }

    @Test
    fun `parser keeps code blocks and ordered lists structured`() {
        val blocks =
            ComposeMarkdownParser.parse(
                """
                ```kotlin
                println("hi")
                ```

                3. first
                4. second
                """.trimIndent(),
            )

        val code = assertIs<ComposeMarkdownBlock.CodeBlock>(blocks[0])
        val list = assertIs<ComposeMarkdownBlock.ListBlock>(blocks[1])

        assertTrue(code.code.contains("println"))
        assertTrue(list.ordered)
        assertEquals(3, list.startNumber)
        assertEquals(2, list.items.size)
    }
}
