package de.heckenmann.visualagent.ui.compose

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `parser keeps bullet lists and nested content`() {
        val blocks = ComposeMarkdownParser.parse("- one\n- two\n  - nested")

        val list = assertIs<ComposeMarkdownBlock.ListBlock>(blocks[0])
        assertFalse(list.ordered)
        assertEquals(2, list.items.size)
        assertTrue(list.items[1].isNotEmpty())
    }

    @Test
    fun `parser keeps tables with header and rows`() {
        val blocks = ComposeMarkdownParser.parse("| A | B |\n|---|---|\n| 1 | 2 |")

        val table = assertIs<ComposeMarkdownBlock.Table>(blocks[0])
        assertTrue(table.headerRow != null || table.rows.isNotEmpty())
    }

    @Test
    fun `parser falls back to raw paragraph for unknown blocks`() {
        val blocks = ComposeMarkdownParser.parse("")
        val paragraph = assertIs<ComposeMarkdownBlock.Paragraph>(blocks[0])
        assertEquals("", paragraph.inlines.single().text)
    }

    @Test
    fun `parser preserves indented code block`() {
        val blocks = ComposeMarkdownParser.parse("    code line")
        val code = assertIs<ComposeMarkdownBlock.CodeBlock>(blocks[0])
        assertTrue(code.code.contains("code line"))
    }
}
