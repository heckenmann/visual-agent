package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.tools.DEFAULT_TOOL_TIMEOUT_SECONDS
import de.heckenmann.visualagent.agent.tools.ToolCancellationToken
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component

/** Model-facing tool for bounded JavaScript orchestration and data processing. */
@Component
class JavaScriptExecuteTool(
    private val executionService: GraalJavaScriptExecutionService,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId(TOOL_ID),
            name = "javascript_execute",
            description =
                "Execute a sandboxed JavaScript program for complex deterministic logic or large " +
                    "CSV/Markdown generation. Use tools.call(name, arguments) for enabled Visual Agent " +
                    "tools, or workspace.write({path, content}), workspace.read({path}), and workspace.delete({path}) " +
                    "to persist, read, or remove " +
                    "UTF-8 text below the managed workspace. Set either source or path to execute a script; path " +
                    "loads a relative JavaScript file from the managed workspace. Return the complete final string, " +
                    "Markdown, or JSON value. " +
                    "Execution errors are returned so the source or arguments can be corrected.",
            inputSchema =
                """{"type":"object","properties":{"source":{"type":"string","description":"Inline JavaScript source; return the final value"},"path":{"type":"string","description":"Workspace-relative JavaScript file to execute"}},"anyOf":[{"required":["source"]},{"required":["path"]}],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input =
            runCatching {
                kotlinx.serialization.json.Json
                    .parseToJsonElement(inputJson)
                    .jsonObject
            }.getOrNull()
                ?: return failure("JavaScript source or path is required")
        val source = (input["source"] as? JsonPrimitive)?.content
        val path = (input["path"] as? JsonPrimitive)?.content
        if (source != null && path != null) return failure("Provide either source or path, not both")
        val timeoutSeconds =
            (context["toolTimeoutSeconds"] as? Number)
                ?.toLong()
                ?: DEFAULT_TOOL_TIMEOUT_SECONDS.toLong()
        val limits = JavaScriptExecutionLimits(timeoutMillis = timeoutSeconds * MILLIS_PER_SECOND)
        val loadedSource =
            source
                ?: path?.let {
                    try {
                        executionService.readWorkspaceSource(it, limits.maxWorkspaceReadBytes)
                    } catch (_: JavaScriptWorkspaceReadLimitExceededException) {
                        return limitExceeded("Workspace script size limit exceeded")
                    } catch (_: Exception) {
                        return toolFailure("Workspace script could not be read")
                    }
                }
                ?: return failure("JavaScript source or path is required")
        val enabledTools = enabledToolIds(context)
        val cancellationToken = CancellationToken()
        val parentCancellationRegistration =
            (context["cancellationToken"] as? CancellationToken)?.onCancelled(cancellationToken::cancel)
        val toolCancellationRegistration =
            (context["toolCancellationToken"] as? ToolCancellationToken)?.onCancelled(cancellationToken::cancel)
        return try {
            val result =
                executionService.execute(
                    JavaScriptExecutionRequest(
                        source = loadedSource,
                        enabledTools = enabledTools,
                        requestContext = context,
                        cancellationToken = cancellationToken,
                        limits = limits,
                    ),
                )
            ToolResult(TOOL_ID, true, resultContent(result.value))
        } catch (error: JavaScriptExecutionException) {
            ToolResult(TOOL_ID, false, "", "${error.category}: ${error.message}")
        } catch (_: Exception) {
            ToolResult(TOOL_ID, false, "", "INTERNAL: JavaScript execution failed")
        } finally {
            toolCancellationRegistration?.close()
            parentCancellationRegistration?.close()
        }
    }

    private fun enabledToolIds(context: Map<String, Any>): Set<String> =
        when (val value = context["enabledTools"]) {
            is Set<*> -> value.mapNotNull { item -> item?.toString()?.removePrefix("ToolId(value=")?.removeSuffix(")") }.toSet()
            is Collection<*> -> value.mapNotNull { it?.toString() }.toSet()
            else -> emptySet()
        }

    private fun resultToJson(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value.toDouble())
            is Map<*, *> -> buildJsonObject { value.forEach { (key, item) -> if (key is String) put(key, resultToJson(item)) } }
            is Iterable<*> -> buildJsonArray { value.forEach { add(resultToJson(it)) } }
            else -> JsonPrimitive(value.toString())
        }

    private fun resultContent(value: Any?): String = if (value is String) value else resultToJson(value).toString()

    private fun failure(message: String): ToolResult = ToolResult(TOOL_ID, false, "", "TOOL_ARGUMENTS: $message")

    private fun toolFailure(message: String): ToolResult = ToolResult(TOOL_ID, false, "", "TOOL_FAILURE: $message")

    private fun limitExceeded(message: String): ToolResult = ToolResult(TOOL_ID, false, "", "LIMIT_EXCEEDED: $message")

    private companion object {
        const val TOOL_ID = "javascript:execute"
        const val MILLIS_PER_SECOND = 1_000L
    }
}
