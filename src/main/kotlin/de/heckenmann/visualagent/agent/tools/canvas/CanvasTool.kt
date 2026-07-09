package de.heckenmann.visualagent.agent.tools.canvas

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.tools.PathResolution
import de.heckenmann.visualagent.agent.tools.STRING_SCHEMA
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.failure
import de.heckenmann.visualagent.agent.tools.parseObject
import de.heckenmann.visualagent.agent.tools.requiredString
import de.heckenmann.visualagent.agent.tools.resolveWorkspacePathOrFailure
import de.heckenmann.visualagent.agent.tools.string
import de.heckenmann.visualagent.agent.tools.success
import de.heckenmann.visualagent.agent.tools.toFunctionName
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.canvas.CanvasPoint
import de.heckenmann.visualagent.error.ToolExecutionException
import de.heckenmann.visualagent.knowledge.ConversationStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import java.util.Base64

/**
 * Tool that lets sub-agents inspect and edit the structured canvas.
 *
 * Use cases: UC-0000032, UC-0000033.
 */
@Component
class CanvasTool(
    private val canvas: CanvasOperations,
    private val conversationStore: ConversationStore,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description =
                "Inspect or edit the canvas. Actions: get, clear, drawText, drawRect, drawLine, drawStroke, drawCircle, insertImage, " +
                    "select, selectAt, moveFigure, resizeFigure, deleteSelectedFigures, saveDocument, openDocument, captureImage. " +
                    "Coordinates are canvas coordinates. " +
                    "Use get before making layout-sensitive changes.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return runCatching {
            when (input.string("action") ?: "get") {
                "get" -> snapshot()
                "clear" -> success(TOOL_ID, json.encodeToString(canvas.clear()))
                "drawText" -> drawText(input)
                "drawRect" -> drawRect(input)
                "drawLine" -> drawLine(input)
                "drawStroke" -> drawStroke(input)
                "drawCircle" -> drawCircle(input)
                "insertImage" -> insertImage(input)
                "select" -> select(input)
                "selectAt" -> selectAt(input)
                "moveFigure" -> moveFigure(input)
                "resizeFigure" -> resizeFigure(input)
                "deleteSelectedFigures" -> deleteSelectedFigures()
                "saveDocument" -> saveDocument(input)
                "openDocument" -> openDocument(input)
                "captureImage" -> captureImage(input, context)
                else -> failure(TOOL_ID, "Unsupported canvas action")
            }
        }.getOrElse { error ->
            failure(TOOL_ID, error.message ?: error::class.simpleName.orEmpty())
        }
    }

    private fun snapshot(): ToolResult = success(TOOL_ID, json.encodeToString(canvas.snapshot()))

    private fun drawText(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(
                canvas.drawText(
                    text = input.requiredString("text"),
                    x = input.requiredDouble("x"),
                    y = input.requiredDouble("y"),
                    color = input.string("color") ?: DEFAULT_TEXT_COLOR,
                ),
            ),
        )

    private fun drawRect(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(
                canvas.drawRect(
                    x = input.requiredDouble("x"),
                    y = input.requiredDouble("y"),
                    width = input.requiredDouble("width"),
                    height = input.requiredDouble("height"),
                    fillColor = input.string("fillColor") ?: DEFAULT_FILL_COLOR,
                    strokeColor = input.string("strokeColor"),
                ),
            ),
        )

    private fun drawLine(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(
                canvas.drawLine(
                    x1 = input.requiredDouble("x1"),
                    y1 = input.requiredDouble("y1"),
                    x2 = input.requiredDouble("x2"),
                    y2 = input.requiredDouble("y2"),
                    color = input.string("color") ?: DEFAULT_STROKE_COLOR,
                    width = input.double("width") ?: DEFAULT_STROKE_WIDTH,
                ),
            ),
        )

    private fun drawStroke(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(
                canvas.drawStroke(
                    points = input.requiredPoints("points"),
                    color = input.string("color") ?: DEFAULT_STROKE_COLOR,
                    width = input.double("width") ?: DEFAULT_STROKE_WIDTH,
                ),
            ),
        )

    private fun drawCircle(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(
                canvas.drawCircle(
                    centerX = input.requiredDouble("centerX"),
                    centerY = input.requiredDouble("centerY"),
                    radius = input.requiredDouble("radius"),
                    fillColor = input.string("fillColor") ?: DEFAULT_FILL_COLOR,
                ),
            ),
        )

    private fun insertImage(input: JsonObject): ToolResult {
        val path = input.requiredString("path")
        return when (val resolved = resolveWorkspacePathOrFailure(TOOL_ID, path)) {
            is PathResolution.Failure -> resolved.result
            is PathResolution.Success ->
                success(
                    TOOL_ID,
                    json.encodeToString(canvas.insertImage(resolved.path.toString())),
                )
        }
    }

    private fun select(input: JsonObject): ToolResult {
        val indices =
            input["indices"]
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
                ?.toSet()
                ?: input.int("index")?.let { setOf(it) }
                ?: emptySet()
        return success(TOOL_ID, json.encodeToString(canvas.selectFigures(indices)))
    }

    private fun selectAt(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(
                canvas.selectAt(
                    x = input.requiredDouble("x"),
                    y = input.requiredDouble("y"),
                ),
            ),
        )

    private fun moveFigure(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(
                canvas.moveFigure(
                    index = input.requiredInt("index"),
                    deltaX = input.requiredDouble("deltaX"),
                    deltaY = input.requiredDouble("deltaY"),
                ),
            ),
        )

    private fun resizeFigure(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(
                canvas.resizeFigure(
                    index = input.requiredInt("index"),
                    width = input.requiredDouble("width"),
                    height = input.requiredDouble("height"),
                ),
            ),
        )

    private fun deleteSelectedFigures(): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(canvas.deleteSelectedFigures()),
        )

    private fun saveDocument(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(canvas.saveDocument(input.string("name") ?: DEFAULT_DOCUMENT_NAME)),
        )

    private fun openDocument(input: JsonObject): ToolResult =
        success(
            TOOL_ID,
            json.encodeToString(
                canvas.openDocument(
                    id = input.string("id"),
                    path = input.string("path"),
                ),
            ),
        )

    private fun captureImage(
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
                sessionId = context["sessionId"]?.toString()?.ifBlank { null } ?: MAIN_SESSION_ID,
                role = "assistant",
                content = "Canvas snapshot (${snapshot.format.uppercase()})",
                metadata = metadata,
            )
        return success(
            TOOL_ID,
            "Saved immutable canvas snapshot $messageId to conversation history (${snapshot.format}, ${snapshot.bytes.size} bytes).",
        )
    }

    private fun JsonObject.requiredDouble(key: String): Double =
        double(key)
            ?: throw ToolExecutionException(
                summary = "Missing required field",
                detail = "The canvas tool input is missing the required numeric field '$key'.",
                retryable = false,
            )

    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.requiredInt(key: String): Int =
        int(key)
            ?: throw ToolExecutionException(
                summary = "Missing required field",
                detail = "The canvas tool input is missing the required integer field '$key'.",
                retryable = false,
            )

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.content?.toIntOrNull()

    private fun JsonObject.requiredPoints(key: String): List<CanvasPoint> {
        val points =
            this[key]?.jsonArray
                ?: throw ToolExecutionException(
                    summary = "Missing required field",
                    detail = "The canvas tool input is missing the required point array '$key'.",
                    retryable = false,
                )
        return points.map { element ->
            val point = element.jsonObject
            CanvasPoint(
                x = point.requiredDouble("x"),
                y = point.requiredDouble("y"),
            )
        }
    }

    private companion object {
        const val TOOL_ID = "canvas"
        const val MAIN_SESSION_ID = "main"
        const val DEFAULT_TEXT_COLOR = "#24292f"
        const val DEFAULT_FILL_COLOR = "#ffffff"
        const val DEFAULT_STROKE_COLOR = "#1f6feb"
        const val DEFAULT_STROKE_WIDTH = 2.0
        const val DEFAULT_DOCUMENT_NAME = "canvas.canvas"
        val json =
            Json {
                encodeDefaults = true
                prettyPrint = true
            }
    }
}
