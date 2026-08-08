package de.heckenmann.visualagent.agent.tools.canvas

import de.heckenmann.visualagent.agent.tools.api.CanvasToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.string
import de.heckenmann.visualagent.agent.tools.success
import kotlinx.serialization.json.JsonObject

/**
 * Handles the `captureImage` canvas action, saving a snapshot to the conversation history.
 */
internal object CanvasCapture {
    internal fun captureImage(
        canvas: CanvasToolPort,
        input: JsonObject,
        context: Map<String, Any>,
    ): ToolResult {
        val snapshot = canvas.captureImage(input.string("format") ?: "png")
        val messageId =
            canvas.saveCapture(
                context["sessionId"]?.toString()?.ifBlank { null } ?: CanvasToolConstants.MAIN_SESSION_ID,
                snapshot,
            )
        return success(
            CanvasToolConstants.TOOL_ID,
            "Saved immutable canvas snapshot $messageId to conversation history (${snapshot.format}, ${snapshot.bytes.size} bytes).",
        )
    }
}
