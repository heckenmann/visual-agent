package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.absolute

/** Shared JSON parser for tool input. */
public val json = Json { ignoreUnknownKeys = true }

/** Default permissive JSON schema used by tools without a richer schema. */
public const val STRING_SCHEMA = """{"type":"object","additionalProperties":true}"""

internal class ToolInputException(
    message: String,
) : IllegalArgumentException(message)

/** Parses tool input into an object, returning an empty object for malformed input. */
public fun parseObject(inputJson: String): JsonObject =
    runCatching { json.parseToJsonElement(inputJson).jsonObject }.getOrElse { JsonObject(emptyMap()) }

/** Reads an optional string property from tool input. */
public fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

/** Reads a required string property from tool input. */
public fun JsonObject.requiredString(key: String): String =
    string(key)
        ?: throw ToolInputException("The tool input is missing the required string field '$key'.")

/** Reads an optional integer property from tool input. */
public fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

/** Reads an optional boolean property from tool input. */
public fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

/**
 * Execution options parsed from model-provided tool input.
 *
 * @property timeoutSeconds Effective timeout for this tool call
 * @property async Whether tool execution should happen asynchronously
 */
public data class ToolExecutionOptions(
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
public fun runtimeOptions(
    input: JsonObject,
    defaultTimeoutSeconds: Int,
): ToolExecutionOptions {
    val timeout = (input.int("timeoutSeconds") ?: defaultTimeoutSeconds).coerceIn(1, 600)
    val async = input.boolean("async") ?: false
    return ToolExecutionOptions(timeoutSeconds = timeout, async = async)
}

/** Returns the normalized process workspace root. */
public fun workspaceRoot(): Path = Path.of(System.getProperty("user.dir")).absolute().normalize()

private fun resolveWorkspacePath(path: String): Path {
    val resolved = workspaceRoot().resolve(path).normalize()
    require(resolved.startsWith(workspaceRoot())) { "Path escapes workspace root" }
    return resolved
}

/** Resolves a workspace-relative path and converts traversal errors to a tool result. */
public fun resolveWorkspacePathOrFailure(
    toolId: String,
    path: String,
): PathResolution =
    runCatching { PathResolution.Success(resolveWorkspacePath(path)) }
        .getOrElse { PathResolution.Failure(failure(toolId, it.message ?: "Invalid path")) }

/** Result of resolving a workspace path. */
public sealed interface PathResolution {
    /**
     * Successful workspace path resolution.
     */
    data class Success(
        val path: Path,
    ) : PathResolution

    /**
     * Failed path resolution represented as a tool result.
     */
    data class Failure(
        val result: ToolResult,
    ) : PathResolution
}

/** Creates a successful tool result. */
public fun success(
    toolId: String,
    content: String,
): ToolResult = ToolResult(toolId, true, content)

/** Creates a failed tool result. */
public fun failure(
    toolId: String,
    error: String,
): ToolResult = ToolResult(toolId, false, "", error)

/** Returns the standard schema for a workspace path argument. */
public fun pathSchema(): String = requiredStringSchema("path")

/** Builds a required single-string JSON schema. */
public fun requiredStringSchema(name: String): String =
    """{"type":"object","properties":{"$name":{"type":"string"}},"required":["$name"]}"""
