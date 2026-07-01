@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    private val parser = Parser.builder().extensions(listOf(AutolinkExtension.create())).build()

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
            else -> ComposeMarkdownBlock.Paragraph(inlineNodes(node))
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
}

internal data class ComposeMarkdownInline(
    val text: String,
    val bold: Boolean = false,
    val code: Boolean = false,
    val linkDestination: String? = null,
)
