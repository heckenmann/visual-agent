package de.heckenmann.visualagent.agent.tools.canvas

import de.heckenmann.visualagent.agent.tools.string
import de.heckenmann.visualagent.canvas.CanvasPoint
import de.heckenmann.visualagent.error.ToolExecutionException
import kotlinx.serialization.json.Json
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
    val json =
        Json {
            encodeDefaults = true
            prettyPrint = true
        }
}

/**
 * JSON parsing helpers for canvas tool input objects.
 */
internal fun JsonObject.requiredDouble(key: String): Double =
    double(key)
        ?: throw ToolExecutionException(
            summary = "Missing required field",
            detail = "The canvas tool input is missing the required numeric field '$key'.",
            retryable = false,
        )

internal fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

internal fun JsonObject.requiredInt(key: String): Int =
    int(key)
        ?: throw ToolExecutionException(
            summary = "Missing required field",
            detail = "The canvas tool input is missing the required integer field '$key'.",
            retryable = false,
        )

internal fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.content?.toIntOrNull()

internal fun JsonObject.requiredString(key: String): String =
    string(key)
        ?: throw ToolExecutionException(
            summary = "Missing required field",
            detail = "The canvas tool input is missing the required string field '$key'.",
            retryable = false,
        )

internal fun JsonObject.requiredPoints(key: String): List<CanvasPoint> {
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
