package de.heckenmann.visualagent.ui.panels.chat

import javafx.scene.CacheHint
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon
import java.io.ByteArrayInputStream
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Creates JavaFX rows for conversation messages.
 *
 * Use cases: UC-0000004, UC-0000032, UC-0000049.
 */
internal class ChatMessageRenderer(
    private val loadingToken: String,
    private val timeFormatter: DateTimeFormatter,
    private val previousRole: (Int) -> String?,
    private val retryAtRow: (HBox) -> Unit,
) {
    /**
     * Creates a message row node.
     */
    fun createMessageRow(
        item: ChatMessage,
        index: Int,
    ): HBox {
        val row = HBox()
        row.styleClass.addAll("chat-row", "chat-row-inner")
        row.styleClass.add(if (item.role == "user") "chat-row-user" else "chat-row-assistant")
        if (item.isToolEvent) row.styleClass.add("chat-row-tool")
        val grouped = index > 0 && previousRole(index) == item.role
        if (grouped) row.styleClass.add("chat-row-grouped")

        val contentArea = createContentArea(item, grouped, row)
        HBox.setHgrow(contentArea, Priority.ALWAYS)
        contentArea.maxWidth = Double.MAX_VALUE
        row.children.addAll(createAvatarSlot(item, grouped), contentArea)
        return row
    }

    private fun createAvatarSlot(
        item: ChatMessage,
        isGrouped: Boolean,
    ): Region {
        if (isGrouped) return Region().apply { styleClass.add("chat-avatar-spacer") }
        val avatar = Label(if (item.role == "user") "You" else "AI")
        if (item.isToolEvent) avatar.text = "Tool"
        avatar.styleClass.addAll("chat-avatar", if (item.role == "user") "chat-avatar-user" else "chat-avatar-assistant")
        if (item.role == "assistant" && item.content == loadingToken) {
            avatar.styleClass.add("chat-avatar-loading-core")
            val ring =
                ProgressIndicator().apply {
                    progress = -1.0
                    styleClass.add("chat-avatar-loading-ring")
                    isMouseTransparent = true
                }
            return StackPane(avatar, ring).apply {
                styleClass.add("chat-avatar-loading-wrap")
                minWidth = 40.0
                prefWidth = 40.0
                maxWidth = 40.0
                minHeight = 40.0
                prefHeight = 40.0
                maxHeight = 40.0
            }
        }
        return avatar
    }

    private fun createContentArea(
        item: ChatMessage,
        isGrouped: Boolean,
        row: HBox,
    ): VBox {
        val contentBody = createMessageBody(item)
        val copyButton = createIconButton(FontAwesomeSolid.COPY, "Copy") { copyToClipboard(item.content) }
        val actionButtons =
            if (item.role == "assistant") {
                HBox(copyButton, createIconButton(FontAwesomeSolid.REDO, "Retry") { retryAtRow(row) })
            } else {
                HBox(copyButton)
            }
        actionButtons.styleClass.add("chat-action-buttons")
        actionButtons.maxWidth = Double.MAX_VALUE
        copyButton.styleClass.add("chat-action-button")
        actionButtons.children
            .filterIsInstance<Button>()
            .forEach { it.styleClass.add("chat-action-button") }

        val area =
            if (isGrouped) {
                VBox(contentBody, actionButtons)
            } else {
                VBox(createHeader(item), contentBody, actionButtons)
            }
        area.styleClass.add("chat-content-area")
        area.isFillWidth = true
        area.maxWidth = Double.MAX_VALUE
        return area
    }

    private fun createHeader(item: ChatMessage): HBox {
        val roleName =
            Label(
                when {
                    item.isToolEvent -> "Tool"
                    item.role == "user" -> "You"
                    else -> "Assistant"
                },
            )
        roleName.styleClass.add("chat-role-label")
        roleName.styleClass.add(if (item.role == "user") "chat-role-label-user" else "chat-role-label-assistant")
        val timeLabel = Label(timeFormatter.format(item.timestamp)).apply { styleClass.add("chat-time-label") }
        return HBox(roleName, timeLabel).apply { styleClass.add("chat-message-header") }
    }

    private fun createIconButton(
        icon: FontAwesomeSolid,
        tooltipText: String,
        onClick: () -> Unit,
    ): Button =
        Button(null, FontIcon(icon)).apply {
            styleClass.add("button-icon")
            tooltip = Tooltip(tooltipText)
            isFocusTraversable = false
            setOnAction { onClick() }
        }

    private fun createMessageBody(item: ChatMessage): Region {
        if (item.content == loadingToken) {
            val loadingLabel = Label("Main agent is working").apply { styleClass.add("assistant-loading") }
            return HBox(loadingLabel).apply { styleClass.add("assistant-loading-row") }
        }
        item.imageData?.let { return createImageBody(item.content, it) }
        if (item.isToolEvent) return createToolEventBody(item)
        return ChatMarkdownRenderer.render(item.content).also {
            it.isCache = true
            it.cacheHint = CacheHint.SPEED
        }
    }

    private fun createImageBody(
        title: String,
        imageData: ImageMessageData,
    ): Region {
        val image = Image(ByteArrayInputStream(imageData.bytes()))
        val imageView =
            ImageView(image).apply {
                isPreserveRatio = true
                fitWidth = 520.0
                styleClass.add("chat-image-preview")
            }
        val titleLabel =
            Label("$title · ${imageData.width}x${imageData.height}").apply {
                styleClass.add("chat-image-title")
            }
        return VBox(titleLabel, imageView).apply { styleClass.add("chat-image-body") }
    }

    private fun createToolEventBody(item: ChatMessage): Region {
        val data = item.toolData
        val status = (data?.status ?: "ok").uppercase()
        val header = createToolHeader(data?.toolId ?: "tool", status, data?.durationMillis?.let { "${it}ms" } ?: "-")
        val detailsText = toolDetails(data)
        if (detailsText.isBlank()) return header
        val details =
            Label(detailsText).apply {
                styleClass.add("chat-tool-details")
                isWrapText = true
                isManaged = false
                isVisible = false
            }
        header.children.add(createToolToggle(details))
        return VBox(header, details).apply { styleClass.add("chat-tool-body") }
    }

    private fun createToolHeader(
        toolId: String,
        status: String,
        duration: String,
    ): HBox {
        val title = Label(toolId).apply { styleClass.add("chat-tool-title") }
        val statusClass =
            when (status) {
                "OK" -> "chat-tool-status-ok"
                "THINKING" -> "chat-tool-status-thinking"
                else -> "chat-tool-status-error"
            }
        val statusLabel =
            Label(status).apply {
                styleClass.addAll("chat-tool-status", statusClass)
            }
        val durationLabel = Label(duration).apply { styleClass.add("chat-tool-duration") }
        return HBox(title, statusLabel, durationLabel).apply { styleClass.add("chat-tool-header") }
    }

    private fun toolDetails(data: ToolMessageData?): String =
        buildString {
            val input = data?.inputJson?.trim().orEmpty()
            val output = data?.resultContent?.trim().orEmpty()
            val error = data?.resultError?.trim().orEmpty()
            if (input.isNotBlank()) appendLine("Input: $input")
            if (output.isNotBlank()) appendLine("Output: $output")
            if (error.isNotBlank()) appendLine("Error: $error")
        }.trim()

    private fun createToolToggle(details: Label): Button =
        Button(null, FontIcon(FontAwesomeSolid.CHEVRON_DOWN)).apply {
            styleClass.addAll("button-icon", "chat-tool-toggle")
            tooltip = Tooltip("Toggle tool details")
            setOnAction {
                val nextVisible = !details.isVisible
                details.isVisible = nextVisible
                details.isManaged = nextVisible
                (graphic as? FontIcon)?.iconCode = if (nextVisible) FontAwesomeSolid.CHEVRON_UP else FontAwesomeSolid.CHEVRON_DOWN
            }
        }

    private fun copyToClipboard(text: String) {
        val clipboard = Clipboard.getSystemClipboard()
        val content = ClipboardContent()
        content.putString(text)
        clipboard.setContent(content)
    }

    private fun ImageMessageData.bytes(): ByteArray =
        Base64
            .getDecoder()
            .decode(dataUrl.substringAfter("base64,"))
}
