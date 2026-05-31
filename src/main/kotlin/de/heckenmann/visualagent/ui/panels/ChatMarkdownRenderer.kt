package de.heckenmann.visualagent.ui.panels

import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.Parser
import org.commonmark.node.Node as MarkdownNode
import org.commonmark.node.Text as MarkdownText

/**
 * Renders CommonMark Markdown into JavaFX nodes for chat messages.
 *
 * Parsing is delegated to `org.commonmark:commonmark`; this class only maps the parsed AST onto the existing
 * JavaFX chat styling.
 */
internal object ChatMarkdownRenderer {
    private val parser = Parser.builder().build()

    /**
     * Parse and render a Markdown chat message.
     *
     * @param markdown Raw Markdown text from a chat message
     * @return JavaFX region containing rendered Markdown blocks
     */
    fun render(markdown: String): Region {
        if (!looksLikeMarkdown(markdown)) {
            return Label(markdown).apply {
                styleClass.addAll("chat-message-content", "chat-md-paragraph")
                isWrapText = true
                maxWidth = Double.MAX_VALUE
            }
        }
        val root = VBox()
        root.styleClass.add("chat-markdown")
        root.isFillWidth = true
        root.maxWidth = Double.MAX_VALUE

        val document = parser.parse(markdown)
        appendBlockChildren(document, root)

        if (root.children.isEmpty()) {
            root.children += textFlow(listOf(Text(markdown)), "chat-md-paragraph")
        }
        return root
    }

    /**
     * Lightweight markdown detector used to bypass full CommonMark parsing for plain text replies.
     */
    private fun looksLikeMarkdown(text: String): Boolean {
        if (text.contains("```")) return true
        if (text.contains("**")) return true
        if (text.contains("`")) return true
        if (text.contains("- ")) return true
        if (text.contains("* ")) return true
        if (text.contains("\n#")) return true
        if (text.startsWith("#")) return true
        if (text.contains("\n1.")) return true
        return false
    }

    private fun appendBlockChildren(
        parent: MarkdownNode,
        target: VBox,
    ) {
        var child = parent.firstChild
        while (child != null) {
            target.children += renderBlock(child)
            child = child.next
        }
    }

    private fun renderBlock(node: MarkdownNode): Node =
        when (node) {
            is Paragraph -> textFlow(inlineNodes(node), "chat-md-paragraph")
            is Heading -> textFlow(inlineNodes(node), "chat-md-heading")
            is FencedCodeBlock -> codeBlock(node.literal)
            is IndentedCodeBlock -> codeBlock(node.literal)
            is OrderedList -> listBlock(node, ordered = true)
            is BulletList -> listBlock(node, ordered = false)
            else -> textFlow(inlineNodes(node), "chat-md-paragraph")
        }

    private fun listBlock(
        list: MarkdownNode,
        ordered: Boolean,
    ): VBox {
        val container = VBox()
        container.styleClass.add("chat-md-list")
        container.isFillWidth = true
        container.maxWidth = Double.MAX_VALUE

        var index = if (list is OrderedList) list.startNumber else 1
        var child = list.firstChild
        while (child != null) {
            if (child is ListItem) {
                container.children += listItem(index, child, ordered)
                index += 1
            }
            child = child.next
        }
        return container
    }

    private fun listItem(
        index: Int,
        item: ListItem,
        ordered: Boolean,
    ): HBox {
        val marker = Label(if (ordered) "$index." else "•")
        marker.styleClass.add("chat-md-list-marker")

        val content = VBox()
        content.styleClass.add("chat-md-list-item-content")
        content.isFillWidth = true
        content.maxWidth = Double.MAX_VALUE
        appendBlockChildren(item, content)
        HBox.setHgrow(content, Priority.ALWAYS)

        return HBox(marker, content).apply {
            styleClass.add("chat-md-list-item")
            maxWidth = Double.MAX_VALUE
        }
    }

    private fun inlineNodes(parent: MarkdownNode): List<Text> {
        val result = mutableListOf<Text>()
        appendInlineChildren(parent, result, bold = false)
        return result.ifEmpty { listOf(Text("")) }
    }

    private fun appendInlineChildren(
        parent: MarkdownNode,
        target: MutableList<Text>,
        bold: Boolean,
    ) {
        var child = parent.firstChild
        while (child != null) {
            when (child) {
                is MarkdownText -> target += plainText(child.literal, bold)
                is Code -> target += inlineCode(child.literal, bold)
                is StrongEmphasis -> appendInlineChildren(child, target, bold = true)
                is SoftLineBreak -> target += plainText("\n", bold)
                is HardLineBreak -> target += plainText("\n", bold)
                else -> appendInlineChildren(child, target, bold)
            }
            child = child.next
        }
    }

    private fun plainText(
        value: String,
        bold: Boolean,
    ): Text =
        Text(value).apply {
            styleClass.add("chat-md-text")
            if (bold) styleClass.add("chat-md-bold")
        }

    private fun inlineCode(
        value: String,
        bold: Boolean,
    ): Text =
        Text(value).apply {
            styleClass.add("chat-md-inline-code")
            if (bold) styleClass.add("chat-md-bold")
        }

    private fun textFlow(
        children: List<Text>,
        styleClassName: String,
    ): TextFlow =
        TextFlow().apply {
            styleClass.addAll("chat-message-content", styleClassName)
            maxWidth = Double.MAX_VALUE
            this.children.addAll(children)
        }

    private fun codeBlock(code: String): Label =
        Label(code.trimEnd()).apply {
            isWrapText = true
            maxWidth = Double.MAX_VALUE
            styleClass.add("chat-md-code-block")
        }
}
