package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.absolute

internal val json = Json { ignoreUnknownKeys = true }
internal const val STRING_SCHEMA = """{"type":"object","additionalProperties":true}"""

internal fun parseObject(inputJson: String): JsonObject =
    runCatching { json.parseToJsonElement(inputJson).jsonObject }.getOrElse { JsonObject(emptyMap()) }

internal fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.requiredString(key: String): String = string(key) ?: throw IllegalArgumentException("Missing required field '$key'")

internal fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

internal fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

/**
 * Execution options parsed from model-provided tool input.
 *
 * @property timeoutSeconds Effective timeout for this tool call
 * @property async Whether tool execution should happen asynchronously
 */
internal data class ToolExecutionOptions(
    val timeoutSeconds: Int,
    val async: Boolean,
)

/**
 * Parses standard tool runtime options from JSON input.
 *
 * Supported fields:
 * - `timeoutSeconds` (int): per-call timeout override
 * - `async` (boolean): execute call asynchronously
 *
 * @param input Parsed JSON input
 * @param defaultTimeoutSeconds Application default timeout
 * @return Sanitized execution options
 */
internal fun runtimeOptions(
    input: JsonObject,
    defaultTimeoutSeconds: Int,
): ToolExecutionOptions {
    val timeout = (input.int("timeoutSeconds") ?: defaultTimeoutSeconds).coerceIn(1, 600)
    val async = input.boolean("async") ?: false
    return ToolExecutionOptions(timeoutSeconds = timeout, async = async)
}

internal fun workspaceRoot(): Path = Path.of(System.getProperty("user.dir")).absolute().normalize()

private fun resolveWorkspacePath(path: String): Path {
    val resolved = workspaceRoot().resolve(path).normalize()
    require(resolved.startsWith(workspaceRoot())) { "Path escapes workspace root" }
    return resolved
}

internal fun resolveWorkspacePathOrFailure(
    toolId: String,
    path: String,
): PathResolution =
    runCatching { PathResolution.Success(resolveWorkspacePath(path)) }
        .getOrElse { PathResolution.Failure(failure(toolId, it.message ?: "Invalid path")) }

internal sealed interface PathResolution {
    /**
     * Represents Success.
     */
    data class Success(
        val path: Path,
    ) : PathResolution

    /**
     * Represents Failure.
     */
    data class Failure(
        val result: ToolResult,
    ) : PathResolution
}

internal fun success(
    toolId: String,
    content: String,
): ToolResult = ToolResult(toolId, true, content)

internal fun failure(
    toolId: String,
    error: String,
): ToolResult = ToolResult(toolId, false, "", error)

internal fun pathSchema(): String = requiredStringSchema("path")

internal fun requiredStringSchema(name: String): String =
    """{"type":"object","properties":{"$name":{"type":"string"}},"required":["$name"]}"""
