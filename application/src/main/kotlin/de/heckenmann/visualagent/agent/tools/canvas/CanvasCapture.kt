package de.heckenmann.visualagent.agent.tools.canvas

import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.tools.string
import de.heckenmann.visualagent.agent.tools.success
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.knowledge.ConversationStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Handles the `captureImage` canvas action, saving a snapshot to the conversation history.
 */
internal object CanvasCapture {
    internal fun captureImage(
        canvas: CanvasOperations,
        conversationStore: ConversationStore,
        input: JsonObject,
        context: Map<String, Any>,
    ): ToolResult {
        val snapshot = canvas.captureImage(input.string("format") ?: "png")
        val encoded = Base64.getEncoder().encodeToString(snapshot.bytes)
        val dataUrl = "data:${snapshot.mimeType};base64,$encoded"
        val metadata =
            buildJsonObject {
                put("type", "image")
                put("source", "canvas")
                put("format", snapshot.format)
                put("mimeType", snapshot.mimeType)
                put("dataUrl", dataUrl)
                put("width", snapshot.width)
                put("height", snapshot.height)
                put("immutable", true)
            }.toString()
        val messageId =
            conversationStore.saveConversationMessage(
                sessionId = context["sessionId"]?.toString()?.ifBlank { null } ?: CanvasToolConstants.MAIN_SESSION_ID,
                role = "assistant",
                content = "Canvas snapshot (${snapshot.format.uppercase()})",
                metadata = metadata,
            )
        return success(
            CanvasToolConstants.TOOL_ID,
            "Saved immutable canvas snapshot $messageId to conversation history (${snapshot.format}, ${snapshot.bytes.size} bytes).",
        )
    }
}
