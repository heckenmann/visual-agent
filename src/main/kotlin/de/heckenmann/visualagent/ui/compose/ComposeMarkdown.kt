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
    val scheme = MaterialTheme.colorScheme
    val blocks = remember(markdown) { ComposeMarkdownParser.parse(markdown) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            MarkdownBlock(
                block = block,
                scheme = scheme,
            )
        }
    }
}

@Composable
private fun MarkdownBlock(
    block: ComposeMarkdownBlock,
    scheme: androidx.compose.material3.ColorScheme,
) {
    when (block) {
        is ComposeMarkdownBlock.CodeBlock ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainer, RoundedCornerShape(10.dp))
                        .border(1.dp, scheme.outline, RoundedCornerShape(10.dp))
                        .padding(10.dp),
            ) {
                Text(
                    block.code.trimEnd(),
                    color = scheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        is ComposeMarkdownBlock.Heading ->
            Text(
                renderInlines(block.inlines),
                color = scheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
        is ComposeMarkdownBlock.ListBlock -> MarkdownList(block, scheme)
        is ComposeMarkdownBlock.Paragraph ->
            Text(
                renderInlines(block.inlines),
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
        is ComposeMarkdownBlock.Table -> MarkdownTable(block, scheme)
    }
}

@Composable
private fun MarkdownTable(
    block: ComposeMarkdownBlock.Table,
    scheme: androidx.compose.material3.ColorScheme,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, scheme.outline, RoundedCornerShape(4.dp))
                .padding(1.dp),
    ) {
        block.headerRow?.let { header ->
            Row(
                modifier = Modifier.fillMaxWidth().background(scheme.surfaceContainer),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                header.forEachIndexed { _, cell ->
                    MarkdownTableCell(
                        cell = cell,
                        isHeader = true,
                        scheme = scheme,
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
                        scheme = scheme,
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
    scheme: androidx.compose.material3.ColorScheme,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .border(0.5.dp, scheme.outline)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = renderInlines(cell.inlines),
            color = scheme.onSurface,
            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MarkdownList(
    block: ComposeMarkdownBlock.ListBlock,
    scheme: androidx.compose.material3.ColorScheme,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (block.ordered) "${block.startNumber + index}." else "-",
                    color = scheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.forEach { MarkdownBlock(it, scheme) }
                }
            }
        }
    }
}

@Composable
private fun renderInlines(inlines: List<ComposeMarkdownInline>) = inlines.toAnnotatedString()

@Composable
private fun List<ComposeMarkdownInline>.toAnnotatedString() =
    buildAnnotatedString {
        forEach { inline ->
            val start = length
            append(inline.text)
            addStyle(inline.style(), start, length)
        }
    }

@Composable
private fun ComposeMarkdownInline.style(): SpanStyle {
    val scheme = MaterialTheme.colorScheme
    return SpanStyle(
        color = if (linkDestination.isNullOrBlank()) Color.Unspecified else scheme.tertiary,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = if (code) FontFamily.Monospace else null,
        background = if (code) scheme.surfaceContainer else Color.Unspecified,
        textDecoration = if (linkDestination.isNullOrBlank()) null else TextDecoration.Underline,
    )
}
