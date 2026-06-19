package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.ui.panels.canvas.CanvasOperations
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

/**
 * Tool that lets sub-agents inspect and edit the structured canvas.
 */
@Component
class CanvasTool(
    private val canvas: CanvasOperations,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = ToolId(TOOL_ID).toFunctionName(),
            description =
                "Inspect or edit the canvas. Actions: get, clear, drawText, drawRect, drawLine, drawCircle, insertImage. " +
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
                "clear" -> success(TOOL_ID, json.encodeToString(canvas.clear()))
                "drawText" -> drawText(input)
                "drawRect" -> drawRect(input)
                "drawLine" -> drawLine(input)
                "drawCircle" -> drawCircle(input)
                "insertImage" -> insertImage(input)
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

    private fun JsonObject.requiredDouble(key: String): Double =
        double(key) ?: throw IllegalArgumentException("Missing required field '$key'")

    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

    private companion object {
        const val TOOL_ID = "canvas"
        const val DEFAULT_TEXT_COLOR = "#24292f"
        const val DEFAULT_FILL_COLOR = "#ffffff"
        const val DEFAULT_STROKE_COLOR = "#1f6feb"
        const val DEFAULT_STROKE_WIDTH = 2.0
        val json =
            Json {
                encodeDefaults = true
                prettyPrint = true
            }
    }
}
