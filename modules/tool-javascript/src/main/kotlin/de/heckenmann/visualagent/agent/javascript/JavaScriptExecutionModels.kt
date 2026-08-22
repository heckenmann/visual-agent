package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition

/** Limits applied to one untrusted JavaScript execution. */
data class JavaScriptExecutionLimits(
    val timeoutMillis: Long = 15_000,
    val maxResultCharacters: Int = 500_000,
    val maxToolCalls: Int = 32,
    val maxConcurrentToolCalls: Int = 4,
    val maxWorkspaceWriteBytes: Long = 50L * 1024L * 1024L,
    val maxWorkspaceBytes: Long = 100L * 1024L * 1024L,
    val maxLogEntries: Int = 100,
    val maxLogCharacters: Int = 10_000,
)

/** Result of a hardened JavaScript workspace write. */
data class JavaScriptWorkspaceWriteResult(
    val relativePath: String,
    val sizeBytes: Long,
    val mimeType: String,
)

/** Result of a hardened JavaScript workspace deletion. */
data class JavaScriptWorkspaceDeleteResult(
    val relativePath: String,
    val deleted: Boolean,
)

/** Reads and mutates workspace files without exposing host filesystem APIs. */
fun interface JavaScriptWorkspaceWriter {
    /**
     * Write UTF-8 text to a workspace-relative path.
     *
     * @param relativePath target path below the managed workspace root
     * @param content complete UTF-8 text to write
     * @return persisted workspace metadata
     */
    fun write(
        relativePath: String,
        content: String,
    ): JavaScriptWorkspaceWriteResult

    /** Read UTF-8 text from a workspace-relative file. */
    fun read(relativePath: String): String = throw UnsupportedOperationException("Workspace reads are not available")

    /** Delete a workspace-relative file. */
    fun delete(relativePath: String): JavaScriptWorkspaceDeleteResult =
        throw UnsupportedOperationException("Workspace deletes are not available")
}

/** Request passed to the JavaScript execution service. */
data class JavaScriptExecutionRequest(
    val source: String,
    val enabledTools: Set<String>,
    val requestContext: Map<String, Any> = emptyMap(),
    val cancellationToken: CancellationToken? = null,
    val limits: JavaScriptExecutionLimits = JavaScriptExecutionLimits(),
)

/** Safe error categories returned by the JavaScript execution tool. */
enum class JavaScriptErrorCategory {
    SYNTAX,
    RUNTIME,
    TOOL_ACCESS,
    TOOL_ARGUMENTS,
    TOOL_FAILURE,
    TIMEOUT,
    LIMIT_EXCEEDED,
    CANCELLED,
    INTERNAL,
}

/** Diagnostic entry collected by the sandbox console implementation. */
data class JavaScriptLogEntry(
    val level: String,
    val message: String,
)

/** Result returned by the execution service before tool serialization. */
data class JavaScriptExecutionResult(
    val value: Any?,
    val logs: List<JavaScriptLogEntry> = emptyList(),
)

/** Failure raised at the JavaScript/tool boundary with a model-safe message. */
class JavaScriptExecutionException(
    val category: JavaScriptErrorCategory,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Read-only metadata exposed by the request-scoped JavaScript tool API. */
data class JavaScriptToolDescription(
    val id: String,
    val name: String,
    val description: String,
    val inputSchema: String,
)

/** Converts a tool definition into metadata safe for a guest runtime. */
fun ToolDefinition.toJavaScriptDescription(): JavaScriptToolDescription =
    JavaScriptToolDescription(
        id = id.value,
        name = name,
        description = description,
        inputSchema = inputSchema,
    )
