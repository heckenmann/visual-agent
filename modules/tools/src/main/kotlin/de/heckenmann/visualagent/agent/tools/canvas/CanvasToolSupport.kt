package de.heckenmann.visualagent.agent.tools.canvas

import de.heckenmann.visualagent.agent.tools.api.ToolCanvasPoint
import de.heckenmann.visualagent.agent.tools.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Constants and JSON helpers shared across canvas tool implementation files.
 */
internal object CanvasToolConstants {
    const val TOOL_ID = "canvas"
    const val MAIN_SESSION_ID = "main"
    const val DEFAULT_TEXT_COLOR = "#24292f"
    const val DEFAULT_FILL_COLOR = "#ffffff"
    const val DEFAULT_STROKE_COLOR = "#1f6feb"
    const val DEFAULT_STROKE_WIDTH = 2.0
    const val DEFAULT_DOCUMENT_NAME = "canvas.canvas"
}

/**
 * JSON parsing helpers for canvas tool input objects.
 */
internal fun JsonObject.requiredDouble(key: String): Double =
    double(key)
        ?: error("Missing required field: The canvas tool input is missing the required numeric field '$key'.")

internal fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

internal fun JsonObject.requiredInt(key: String): Int =
    int(key)
        ?: error("Missing required field: The canvas tool input is missing the required integer field '$key'.")

internal fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.content?.toIntOrNull()

internal fun JsonObject.requiredString(key: String): String =
    string(key)
        ?: error("Missing required field: The canvas tool input is missing the required string field '$key'.")

internal fun JsonObject.requiredPoints(key: String): List<ToolCanvasPoint> {
    val points =
        this[key]?.jsonArray
            ?: error("Missing required field: The canvas tool input is missing the required point array '$key'.")
    return points.map { element ->
        val point = element.jsonObject
        ToolCanvasPoint(
            x = point.requiredDouble("x"),
            y = point.requiredDouble("y"),
        )
    }
}
