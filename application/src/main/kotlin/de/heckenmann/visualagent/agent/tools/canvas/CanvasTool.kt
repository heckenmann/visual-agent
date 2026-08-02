@file:Suppress("FunctionName")

package de.heckenmann.visualagent.agent.tools.canvas

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.tools.PathResolution
import de.heckenmann.visualagent.agent.tools.STRING_SCHEMA
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.failure
import de.heckenmann.visualagent.agent.tools.parseObject
import de.heckenmann.visualagent.agent.tools.resolveWorkspacePathOrFailure
import de.heckenmann.visualagent.agent.tools.string
import de.heckenmann.visualagent.agent.tools.success
import de.heckenmann.visualagent.agent.tools.toFunctionName
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.knowledge.ConversationStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

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
            id = ToolId(CanvasToolConstants.TOOL_ID),
            name = ToolId(CanvasToolConstants.TOOL_ID).toFunctionName(),
            description =
                "Inspect or edit the canvas (a 2D drawing board). Actions:\n" +
                    "- get: {\"action\":\"get\"}. Returns all figures on the canvas.\n" +
                    "- clear: {\"action\":\"clear\"}. Removes all figures.\n" +
                    "- drawText: {\"action\":\"drawText\",\"text\":\"hello\",\"x\":100,\"y\":200,\"color\":\"#24292f\"}.\n" +
                    "- drawRect: {\"action\":\"drawRect\",\"x\":100,\"y\":200,\"width\":300,\"height\":150," +
                    "\"fillColor\":\"#ffffff\",\"strokeColor\":\"#1f6feb\"}.\n" +
                    "- drawLine: {\"action\":\"drawLine\",\"x1\":100,\"y1\":200,\"x2\":300,\"y2\":400," +
                    "\"color\":\"#1f6feb\",\"width\":2}.\n" +
                    "- drawStroke: {\"action\":\"drawStroke\"," +
                    "\"points\":[{\"x\":100,\"y\":200},{\"x\":150,\"y\":250}],\"color\":\"#1f6feb\",\"width\":2}.\n" +
                    "- drawCircle: {\"action\":\"drawCircle\",\"centerX\":200,\"centerY\":200," +
                    "\"radius\":50,\"fillColor\":\"#ffffff\"}.\n" +
                    "- insertImage: {\"action\":\"insertImage\",\"path\":\"relative/path/image.png\"}.\n" +
                    "- select: {\"action\":\"select\",\"indices\":[0,1]} or {\"action\":\"select\",\"index\":0}.\n" +
                    "- selectAt: {\"action\":\"selectAt\",\"x\":100,\"y\":200}. Select figure at coordinates.\n" +
                    "- moveFigure: {\"action\":\"moveFigure\",\"index\":0,\"deltaX\":50,\"deltaY\":30}.\n" +
                    "- resizeFigure: {\"action\":\"resizeFigure\",\"index\":0,\"width\":200,\"height\":100}.\n" +
                    "- deleteSelectedFigures: {\"action\":\"deleteSelectedFigures\"}.\n" +
                    "- saveDocument: {\"action\":\"saveDocument\",\"name\":\"my.canvas\"}. Saves to workspace/canvas/.\n" +
                    "- openDocument: {\"action\":\"openDocument\",\"id\":\"...\"} or " +
                    "{\"action\":\"openDocument\",\"path\":\"...\"}.\n" +
                    "- captureImage: {\"action\":\"captureImage\",\"format\":\"png\"}. " +
                    "Saves snapshot to conversation. " +
                    "Coordinates are canvas coordinates. Use get before making layout-sensitive changes.",
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
                "clear" -> success(CanvasToolConstants.TOOL_ID, CanvasToolConstants.json.encodeToString(canvas.clear()))
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
                "captureImage" -> CanvasCapture.captureImage(canvas, conversationStore, input, context)
                else -> failure(CanvasToolConstants.TOOL_ID, "Unsupported canvas action")
            }
        }.getOrElse { error ->
            failure(CanvasToolConstants.TOOL_ID, error.message ?: error::class.simpleName.orEmpty())
        }
    }

    private fun snapshot(): ToolResult = success(CanvasToolConstants.TOOL_ID, CanvasToolConstants.json.encodeToString(canvas.snapshot()))

    private fun drawText(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(
                canvas.drawText(
                    text = input.requiredString("text"),
                    x = input.requiredDouble("x"),
                    y = input.requiredDouble("y"),
                    color = input.string("color") ?: CanvasToolConstants.DEFAULT_TEXT_COLOR,
                ),
            ),
        )

    private fun drawRect(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(
                canvas.drawRect(
                    x = input.requiredDouble("x"),
                    y = input.requiredDouble("y"),
                    width = input.requiredDouble("width"),
                    height = input.requiredDouble("height"),
                    fillColor = input.string("fillColor") ?: CanvasToolConstants.DEFAULT_FILL_COLOR,
                    strokeColor = input.string("strokeColor"),
                ),
            ),
        )

    private fun drawLine(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(
                canvas.drawLine(
                    x1 = input.requiredDouble("x1"),
                    y1 = input.requiredDouble("y1"),
                    x2 = input.requiredDouble("x2"),
                    y2 = input.requiredDouble("y2"),
                    color = input.string("color") ?: CanvasToolConstants.DEFAULT_STROKE_COLOR,
                    width = input.double("width") ?: CanvasToolConstants.DEFAULT_STROKE_WIDTH,
                ),
            ),
        )

    private fun drawStroke(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(
                canvas.drawStroke(
                    points = input.requiredPoints("points"),
                    color = input.string("color") ?: CanvasToolConstants.DEFAULT_STROKE_COLOR,
                    width = input.double("width") ?: CanvasToolConstants.DEFAULT_STROKE_WIDTH,
                ),
            ),
        )

    private fun drawCircle(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(
                canvas.drawCircle(
                    centerX = input.requiredDouble("centerX"),
                    centerY = input.requiredDouble("centerY"),
                    radius = input.requiredDouble("radius"),
                    fillColor = input.string("fillColor") ?: CanvasToolConstants.DEFAULT_FILL_COLOR,
                ),
            ),
        )

    private fun insertImage(input: JsonObject): ToolResult {
        val path = input.requiredString("path")
        return when (val resolved = resolveWorkspacePathOrFailure(CanvasToolConstants.TOOL_ID, path)) {
            is PathResolution.Failure -> resolved.result
            is PathResolution.Success ->
                success(
                    CanvasToolConstants.TOOL_ID,
                    CanvasToolConstants.json.encodeToString(canvas.insertImage(resolved.path.toString())),
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
        return success(CanvasToolConstants.TOOL_ID, CanvasToolConstants.json.encodeToString(canvas.selectFigures(indices)))
    }

    private fun selectAt(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(
                canvas.selectAt(
                    x = input.requiredDouble("x"),
                    y = input.requiredDouble("y"),
                ),
            ),
        )

    private fun moveFigure(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(
                canvas.moveFigure(
                    index = input.requiredInt("index"),
                    deltaX = input.requiredDouble("deltaX"),
                    deltaY = input.requiredDouble("deltaY"),
                ),
            ),
        )

    private fun resizeFigure(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(
                canvas.resizeFigure(
                    index = input.requiredInt("index"),
                    width = input.requiredDouble("width"),
                    height = input.requiredDouble("height"),
                ),
            ),
        )

    private fun deleteSelectedFigures(): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(canvas.deleteSelectedFigures()),
        )

    private fun saveDocument(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(canvas.saveDocument(input.string("name") ?: CanvasToolConstants.DEFAULT_DOCUMENT_NAME)),
        )

    private fun openDocument(input: JsonObject): ToolResult =
        success(
            CanvasToolConstants.TOOL_ID,
            CanvasToolConstants.json.encodeToString(
                canvas.openDocument(
                    id = input.string("id"),
                    path = input.string("path"),
                ),
            ),
        )
}
