@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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

@Composable
internal fun ComposeMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { ComposeMarkdownParser.parse(markdown) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            MarkdownBlock(block)
        }
    }
}

@Composable
private fun MarkdownBlock(block: ComposeMarkdownBlock) {
    when (block) {
        is ComposeMarkdownBlock.CodeBlock ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF191A21), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x33444A65), RoundedCornerShape(10.dp))
                        .padding(10.dp),
            ) {
                Text(
                    block.code.trimEnd(),
                    color = Color(0xFFF8F8F2),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        is ComposeMarkdownBlock.Heading ->
            Text(
                block.inlines.toAnnotatedString(),
                color = Color(0xFFF8F8F2),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
        is ComposeMarkdownBlock.ListBlock -> MarkdownList(block)
        is ComposeMarkdownBlock.Paragraph ->
            Text(
                block.inlines.toAnnotatedString(),
                color = Color(0xFFF8F8F2),
                style = MaterialTheme.typography.bodySmall,
            )
        is ComposeMarkdownBlock.Table -> MarkdownTable(block)
    }
}

@Composable
private fun MarkdownTable(block: ComposeMarkdownBlock.Table) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x33444A65), RoundedCornerShape(4.dp))
                .padding(1.dp),
    ) {
        block.headerRow?.let { header ->
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF191A21)),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                header.forEachIndexed { _, cell ->
                    MarkdownTableCell(
                        cell = cell,
                        isHeader = true,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        block.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                row.forEachIndexed { _, cell ->
                    MarkdownTableCell(
                        cell = cell,
                        isHeader = false,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableCell(
    cell: TableCell,
    isHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .border(0.5.dp, Color(0x22444A65))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = cell.inlines.toAnnotatedString(),
            color = Color(0xFFF8F8F2),
            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MarkdownList(block: ComposeMarkdownBlock.ListBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (block.ordered) "${block.startNumber + index}." else "-",
                    color = Color(0xFFFFB86C),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.forEach { MarkdownBlock(it) }
                }
            }
        }
    }
}

private fun List<ComposeMarkdownInline>.toAnnotatedString() =
    buildAnnotatedString {
        forEach { inline ->
            val start = length
            append(inline.text)
            addStyle(inline.style(), start, length)
        }
    }

private fun ComposeMarkdownInline.style(): SpanStyle =
    SpanStyle(
        color = if (linkDestination.isNullOrBlank()) Color.Unspecified else Color(0xFF8BE9FD),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = if (code) FontFamily.Monospace else null,
        background = if (code) Color(0xFF191A21) else Color.Unspecified,
        textDecoration = if (linkDestination.isNullOrBlank()) null else TextDecoration.Underline,
    )

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
