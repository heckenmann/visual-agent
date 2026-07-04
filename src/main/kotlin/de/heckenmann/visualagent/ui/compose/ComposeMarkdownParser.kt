@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.TableCell as CommonMarkTableCell
import org.commonmark.node.Node as MarkdownNode
import org.commonmark.node.Text as MarkdownText

internal object ComposeMarkdownParser {
    private val parser =
        Parser
            .builder()
            .extensions(listOf(AutolinkExtension.create(), TablesExtension.create()))
            .build()

    fun parse(markdown: String): List<ComposeMarkdownBlock> {
        val document = parser.parse(markdown)
        val blocks = blockChildren(document)
        return blocks.ifEmpty {
            listOf(ComposeMarkdownBlock.Paragraph(listOf(ComposeMarkdownInline(markdown))))
        }
    }

    private fun blockChildren(parent: MarkdownNode): List<ComposeMarkdownBlock> {
        val result = mutableListOf<ComposeMarkdownBlock>()
        var child = parent.firstChild
        while (child != null) {
            result += renderBlock(child)
            child = child.next
        }
        return result
    }

    private fun renderBlock(node: MarkdownNode): ComposeMarkdownBlock =
        when (node) {
            is Paragraph -> ComposeMarkdownBlock.Paragraph(inlineNodes(node))
            is Heading -> ComposeMarkdownBlock.Heading(node.level, inlineNodes(node))
            is FencedCodeBlock -> ComposeMarkdownBlock.CodeBlock(node.literal)
            is IndentedCodeBlock -> ComposeMarkdownBlock.CodeBlock(node.literal)
            is OrderedList -> listBlock(node, ordered = true)
            is BulletList -> listBlock(node, ordered = false)
            is TableBlock -> tableBlock(node)
            else -> ComposeMarkdownBlock.Paragraph(inlineNodes(node))
        }

    private fun tableBlock(table: TableBlock): ComposeMarkdownBlock.Table {
        val headerRow = mutableListOf<TableCell>()
        val rows = mutableListOf<List<TableCell>>()
        var child = table.firstChild
        while (child != null) {
            when (child) {
                is TableHead -> headerRow += tableRowCells(child)
                is TableBody -> {
                    var rowChild = child.firstChild
                    while (rowChild != null) {
                        if (rowChild is TableRow) {
                            rows += tableRowCells(rowChild)
                        }
                        rowChild = rowChild.next
                    }
                }
            }
            child = child.next
        }
        return ComposeMarkdownBlock.Table(
            headerRow = headerRow.ifEmpty { null },
            rows = rows,
        )
    }

    private fun tableRowCells(row: MarkdownNode): List<TableCell> {
        val cells = mutableListOf<TableCell>()
        var child = row.firstChild
        while (child != null) {
            if (child is CommonMarkTableCell) {
                cells += TableCell(inlineNodes(child))
            }
            child = child.next
        }
        return cells
    }

    private fun listBlock(
        list: MarkdownNode,
        ordered: Boolean,
    ): ComposeMarkdownBlock.ListBlock {
        val items = mutableListOf<List<ComposeMarkdownBlock>>()
        var child = list.firstChild
        while (child != null) {
            if (child is ListItem) items += blockChildren(child)
            child = child.next
        }
        return ComposeMarkdownBlock.ListBlock(
            ordered = ordered,
            startNumber = if (list is OrderedList) list.markerStartNumber ?: 1 else 1,
            items = items,
        )
    }

    private fun inlineNodes(parent: MarkdownNode): List<ComposeMarkdownInline> {
        val result = mutableListOf<ComposeMarkdownInline>()
        appendInlineChildren(parent, result, bold = false, linkDestination = null)
        return result.ifEmpty { listOf(ComposeMarkdownInline("")) }
    }

    private fun appendInlineChildren(
        parent: MarkdownNode,
        target: MutableList<ComposeMarkdownInline>,
        bold: Boolean,
        linkDestination: String?,
    ) {
        var child = parent.firstChild
        while (child != null) {
            when (child) {
                is MarkdownText -> target += ComposeMarkdownInline(child.literal, bold = bold, linkDestination = linkDestination)
                is Code -> target += ComposeMarkdownInline(child.literal, bold = bold, code = true, linkDestination = linkDestination)
                is StrongEmphasis -> appendInlineChildren(child, target, bold = true, linkDestination = linkDestination)
                is Link -> appendInlineChildren(child, target, bold = bold, linkDestination = child.destination)
                is SoftLineBreak -> target += ComposeMarkdownInline("\n", bold = bold, linkDestination = linkDestination)
                is HardLineBreak -> target += ComposeMarkdownInline("\n", bold = bold, linkDestination = linkDestination)
                else -> appendInlineChildren(child, target, bold, linkDestination)
            }
            child = child.next
        }
    }
}

internal sealed interface ComposeMarkdownBlock {
    data class Paragraph(
        val inlines: List<ComposeMarkdownInline>,
    ) : ComposeMarkdownBlock

    data class Heading(
        val level: Int,
        val inlines: List<ComposeMarkdownInline>,
    ) : ComposeMarkdownBlock

    data class CodeBlock(
        val code: String,
    ) : ComposeMarkdownBlock

    data class ListBlock(
        val ordered: Boolean,
        val startNumber: Int,
        val items: List<List<ComposeMarkdownBlock>>,
    ) : ComposeMarkdownBlock

    data class Table(
        val headerRow: List<TableCell>?,
        val rows: List<List<TableCell>>,
    ) : ComposeMarkdownBlock
}

internal data class TableCell(
    val inlines: List<ComposeMarkdownInline>,
)

internal data class ComposeMarkdownInline(
    val text: String,
    val bold: Boolean = false,
    val code: Boolean = false,
    val linkDestination: String? = null,
)
