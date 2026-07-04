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
                renderInlines(block.inlines),
                color = Color(0xFFF8F8F2),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
        is ComposeMarkdownBlock.ListBlock -> MarkdownList(block)
        is ComposeMarkdownBlock.Paragraph ->
            Text(
                renderInlines(block.inlines),
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
            text = renderInlines(cell.inlines),
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

private fun renderInlines(inlines: List<ComposeMarkdownInline>) = inlines.toAnnotatedString()

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
