package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.CanvasToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolCanvasImage
import de.heckenmann.visualagent.agent.tools.api.ToolCanvasPoint
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.canvas.CanvasPoint
import de.heckenmann.visualagent.knowledge.ConversationStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import java.util.Base64

/** Application adapter for canvas operations and snapshot persistence consumed by tools. */
@Component
class CanvasToolPortAdapter(
    private val canvas: CanvasOperations,
    private val conversations: ConversationStore,
) : CanvasToolPort {
    override fun snapshot(): String = encode(canvas.snapshot())

    override fun clear(): String = encode(canvas.clear())

    override fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String,
    ): String = encode(canvas.drawText(text, x, y, color))

    override fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String?,
    ): String = encode(canvas.drawRect(x, y, width, height, fillColor, strokeColor))

    override fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
    ): String = encode(canvas.drawLine(x1, y1, x2, y2, color, width))

    override fun drawStroke(
        points: List<ToolCanvasPoint>,
        color: String,
        width: Double,
    ): String = encode(canvas.drawStroke(points.map { CanvasPoint(it.x, it.y) }, color, width))

    override fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ): String = encode(canvas.drawCircle(centerX, centerY, radius, fillColor))

    override fun insertImage(path: String): String = encode(canvas.insertImage(path))

    override fun select(indices: Set<Int>): String = encode(canvas.selectFigures(indices))

    override fun selectAt(
        x: Double,
        y: Double,
    ): String = encode(canvas.selectAt(x, y))

    override fun moveFigure(
        index: Int,
        deltaX: Double,
        deltaY: Double,
    ): String = encode(canvas.moveFigure(index, deltaX, deltaY))

    override fun resizeFigure(
        index: Int,
        width: Double,
        height: Double,
    ): String = encode(canvas.resizeFigure(index, width, height))

    override fun deleteSelectedFigures(): String = encode(canvas.deleteSelectedFigures())

    override fun saveDocument(name: String): String = encode(canvas.saveDocument(name))

    override fun openDocument(
        id: String?,
        path: String?,
    ): String = encode(canvas.openDocument(id, path))

    override fun captureImage(format: String): ToolCanvasImage =
        canvas.captureImage(format).let { ToolCanvasImage(it.format, it.mimeType, it.bytes, it.width, it.height) }

    override fun saveCapture(
        sessionId: String,
        image: ToolCanvasImage,
    ): String {
        val metadata =
            buildJsonObject {
                put("type", "image")
                put("source", "canvas")
                put("format", image.format)
                put("mimeType", image.mimeType)
                put("dataUrl", "data:${image.mimeType};base64,${Base64.getEncoder().encodeToString(image.bytes)}")
                put("width", image.width)
                put("height", image.height)
                put("immutable", true)
            }.toString()
        return conversations.saveConversationMessage(
            sessionId,
            "assistant",
            "Canvas snapshot (${image.format.uppercase()})",
            metadata,
        )
    }

    private inline fun <reified T> encode(value: T): String = JSON.encodeToString(value)

    private companion object {
        val JSON =
            Json {
                encodeDefaults = true
                prettyPrint = true
            }
    }
}
