package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyExecutable
import org.graalvm.polyglot.proxy.ProxyObject
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Request-scoped bridge exposing only enabled registry tools to JavaScript. */
internal class JavaScriptToolBridge(
    private val registry: ToolRegistry,
    private val workspaceWriter: JavaScriptWorkspaceWriter,
    private val enabledTools: Set<String>,
    private val requestContext: Map<String, Any>,
    private val cancellationToken: CancellationToken,
    private val limits: JavaScriptExecutionLimits,
    private val logs: MutableList<JavaScriptLogEntry>,
) {
    /** Last bridge error, retained because Graal may erase host exception details in a Promise. */
    val lastFailure = AtomicReference<JavaScriptExecutionException?>()
    private val calls = AtomicInteger()
    private val workspaceReadBytes = AtomicLong()
    private val workspaceBytes = AtomicLong()
    private val permits = Semaphore(limits.maxConcurrentToolCalls)
    private val valueConverter = JavaScriptGuestValueConverter(limits)

    /** Returns the only host object made available to the guest context. */
    fun toolsObject(): ProxyObject =
        ProxyObject.fromMap(
            mapOf(
                "call" to
                    executable { arguments ->
                        try {
                            call(arguments)
                        } catch (error: JavaScriptExecutionException) {
                            lastFailure.set(error)
                            throw error
                        }
                    },
                "list" to executable { listTools() },
                "describe" to executable(::describe),
                "workspace" to workspaceObject(),
            ),
        )

    /** Returns the hardened workspace write API exposed to the guest runtime. */
    fun workspaceObject(): ProxyObject =
        ProxyObject.fromMap(
            mapOf(
                "write" to
                    executable { arguments ->
                        try {
                            writeWorkspaceFile(arguments)
                        } catch (error: JavaScriptExecutionException) {
                            lastFailure.set(error)
                            throw error
                        }
                    },
                "read" to
                    executable { arguments ->
                        try {
                            cancellationToken.throwIfCancelled()
                            consumeCall()
                            val read =
                                workspaceWriter.read(
                                    workspacePath(arguments),
                                    availableWorkspaceReadBytes(),
                                )
                            reserveWorkspaceReadBytes(read.sizeBytes)
                            read.content
                        } catch (error: JavaScriptExecutionException) {
                            lastFailure.set(error)
                            throw error
                        } catch (_: JavaScriptWorkspaceReadLimitExceededException) {
                            throw workspaceFailure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Workspace read size limit exceeded")
                        } catch (error: IllegalArgumentException) {
                            throw workspaceFailure(JavaScriptErrorCategory.TOOL_ARGUMENTS, error.message ?: "Invalid workspace path")
                        } catch (error: Exception) {
                            throw workspaceFailure(JavaScriptErrorCategory.TOOL_FAILURE, "Workspace read failed")
                        }
                    },
                "delete" to
                    executable { arguments ->
                        try {
                            cancellationToken.throwIfCancelled()
                            consumeCall()
                            val result = workspaceWriter.delete(workspacePath(arguments))
                            ProxyObject.fromMap(mapOf("path" to result.relativePath, "deleted" to result.deleted))
                        } catch (error: JavaScriptExecutionException) {
                            lastFailure.set(error)
                            throw error
                        } catch (error: IllegalArgumentException) {
                            throw workspaceFailure(JavaScriptErrorCategory.TOOL_ARGUMENTS, error.message ?: "Invalid workspace path")
                        } catch (error: Exception) {
                            throw workspaceFailure(JavaScriptErrorCategory.TOOL_FAILURE, "Workspace delete failed")
                        }
                    },
            ),
        )

    /** Returns a bounded console object that never writes to process stdout. */
    fun consoleObject(): ProxyObject =
        ProxyObject.fromMap(
            mapOf(
                "log" to executable { recordLog("log", it) },
                "info" to executable { recordLog("info", it) },
                "warn" to executable { recordLog("warn", it) },
                "error" to executable { recordLog("error", it) },
            ),
        )

    private fun call(arguments: Array<out Value>): Any? {
        cancellationToken.throwIfCancelled()
        val name =
            arguments
                .firstOrNull()
                ?.asString()
                ?.trim()
                .orEmpty()
        if (name.isBlank()) throw failure(JavaScriptErrorCategory.TOOL_ARGUMENTS, "Tool name is required")
        if (name !in enabledTools) throw failure(JavaScriptErrorCategory.TOOL_ACCESS, "Tool '$name' is not enabled")
        if (name == JAVASCRIPT_TOOL_ID) throw failure(JavaScriptErrorCategory.TOOL_ACCESS, "Recursive JavaScript execution is disabled")
        val input =
            arguments.getOrNull(1)?.let(valueConverter::toJsonObject)
                ?: throw failure(JavaScriptErrorCategory.TOOL_ARGUMENTS, "Tool arguments must be an object")
        if ((input["async"] as? JsonPrimitive)?.content?.toBoolean() == true) {
            throw failure(JavaScriptErrorCategory.TOOL_ARGUMENTS, "Nested JavaScript tool calls must be awaited")
        }
        consumeCall()
        if (!permits.tryAcquire()) throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Concurrent JavaScript tool-call limit exceeded")
        return try {
            cancellationToken.throwIfCancelled()
            val tool =
                registry.resolve(setOf(ToolId(name))).singleOrNull()
                    ?: throw failure(JavaScriptErrorCategory.TOOL_ACCESS, "Tool '$name' is not registered")
            val resultJson = registry.execute(tool, input.toString(), requestContext + mapOf("javascript" to true))
            cancellationToken.throwIfCancelled()
            val result = Json.decodeFromString<ToolResult>(resultJson)
            if (!result.success) throw failure(JavaScriptErrorCategory.TOOL_FAILURE, result.error ?: "Tool '$name' failed")
            contentToGuest(result.content)
        } catch (error: JavaScriptExecutionException) {
            throw error
        } catch (error: Exception) {
            throw failure(JavaScriptErrorCategory.TOOL_FAILURE, "Tool '$name' failed: ${safeMessage(error)}")
        } finally {
            permits.release()
        }
    }

    private fun listTools(): Any =
        ProxyArray.fromList(
            enabledTools.sorted().mapNotNull { name ->
                registry
                    .resolve(setOf(ToolId(name)))
                    .singleOrNull()
                    ?.let(registry::definition)
                    ?.toJavaScriptDescription()
                    ?.let(::descriptionObject)
            },
        )

    private fun describe(arguments: Array<out Value>): Any {
        val name =
            arguments
                .firstOrNull()
                ?.asString()
                ?.trim()
                .orEmpty()
        if (name !in enabledTools) throw failure(JavaScriptErrorCategory.TOOL_ACCESS, "Tool '$name' is not enabled")
        val definition =
            registry.resolve(setOf(ToolId(name))).singleOrNull()?.let(registry::definition)
                ?: throw failure(JavaScriptErrorCategory.TOOL_ACCESS, "Tool '$name' is not registered")
        return descriptionObject(definition.toJavaScriptDescription())
    }

    private fun writeWorkspaceFile(arguments: Array<out Value>): Any {
        cancellationToken.throwIfCancelled()
        val input =
            arguments.firstOrNull()?.let(valueConverter::toJsonObject)
                ?: throw failure(JavaScriptErrorCategory.TOOL_ARGUMENTS, "Workspace write arguments must be an object")
        val path = workspacePath(input)
        val content = (input["content"] as? JsonPrimitive)?.content
        if (path.isBlank() || content == null) {
            throw failure(JavaScriptErrorCategory.TOOL_ARGUMENTS, "Workspace write requires string path and content")
        }
        consumeCall()
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        reserveWorkspaceBytes(contentBytes.size.toLong())
        return try {
            val result = workspaceWriter.write(path, content)
            ProxyObject.fromMap(
                mapOf(
                    "path" to result.relativePath,
                    "sizeBytes" to result.sizeBytes,
                    "mimeType" to result.mimeType,
                ),
            )
        } catch (error: IllegalArgumentException) {
            workspaceBytes.addAndGet(-contentBytes.size.toLong())
            throw failure(JavaScriptErrorCategory.TOOL_ARGUMENTS, error.message ?: "Invalid workspace path")
        } catch (error: Exception) {
            workspaceBytes.addAndGet(-contentBytes.size.toLong())
            throw failure(JavaScriptErrorCategory.TOOL_FAILURE, "Workspace write failed")
        }
    }

    private fun workspacePath(arguments: Array<out Value>): String {
        val input =
            arguments.firstOrNull()?.let(valueConverter::toJsonObject)
                ?: throw failure(JavaScriptErrorCategory.TOOL_ARGUMENTS, "Workspace arguments must be an object")
        return workspacePath(input)
    }

    private fun workspacePath(input: JsonObject): String =
        (input["path"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
            ?: throw failure(JavaScriptErrorCategory.TOOL_ARGUMENTS, "Workspace path is required")

    private fun workspaceFailure(
        category: JavaScriptErrorCategory,
        message: String,
    ): JavaScriptExecutionException = failure(category, message).also(lastFailure::set)

    private fun descriptionObject(description: JavaScriptToolDescription): ProxyObject =
        ProxyObject.fromMap(
            mapOf(
                "id" to description.id,
                "name" to description.name,
                "description" to description.description,
                "inputSchema" to description.inputSchema,
            ),
        )

    private fun recordLog(
        level: String,
        arguments: Array<out Value>,
    ): Any? {
        if (logs.size >= limits.maxLogEntries) return null
        val remaining = (limits.maxLogCharacters - logs.sumOf { it.message.length }).coerceAtLeast(0)
        if (remaining == 0) return null
        logs += JavaScriptLogEntry(level, valueConverter.logText(arguments, remaining))
        return null
    }

    private fun contentToGuest(content: String): Any? {
        val element = runCatching { Json.parseToJsonElement(content) }.getOrNull() ?: return content
        return jsonToGuest(element)
    }

    private fun jsonToGuest(element: JsonElement): Any? =
        when (element) {
            JsonNull -> null
            is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNumber()
            is JsonArray -> ProxyArray.fromList(element.map(::jsonToGuest))
            is JsonObject -> ProxyObject.fromMap(element.mapValues { (_, value) -> jsonToGuest(value) })
        }

    private fun executable(action: (Array<out Value>) -> Any?): ProxyExecutable = ProxyExecutable { arguments -> action(arguments) }

    private fun failure(
        category: JavaScriptErrorCategory,
        message: String,
    ): JavaScriptExecutionException = JavaScriptExecutionException(category, message.take(MAX_ERROR_CHARACTERS))

    private fun consumeCall() {
        if (calls.incrementAndGet() > limits.maxToolCalls) {
            throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "JavaScript tool-call limit exceeded")
        }
    }

    private fun reserveWorkspaceBytes(bytes: Long) {
        if (bytes > limits.maxWorkspaceWriteBytes) {
            throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Workspace write size limit exceeded")
        }
        while (true) {
            val current = workspaceBytes.get()
            val next = current + bytes
            if (next < current || next > limits.maxWorkspaceBytes) {
                throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Workspace write budget exceeded")
            }
            if (workspaceBytes.compareAndSet(current, next)) return
        }
    }

    private fun availableWorkspaceReadBytes(): Long =
        minOf(
            limits.maxWorkspaceReadBytes,
            (limits.maxWorkspaceReadTotalBytes - workspaceReadBytes.get()).coerceAtLeast(0),
        )

    private fun reserveWorkspaceReadBytes(bytes: Long) {
        if (bytes < 0 || bytes > limits.maxWorkspaceReadBytes) {
            throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Workspace read size limit exceeded")
        }
        while (true) {
            val current = workspaceReadBytes.get()
            val next = current + bytes
            if (next < current || next > limits.maxWorkspaceReadTotalBytes) {
                throw failure(JavaScriptErrorCategory.LIMIT_EXCEEDED, "Workspace read budget exceeded")
            }
            if (workspaceReadBytes.compareAndSet(current, next)) return
        }
    }

    private fun safeMessage(error: Throwable): String =
        error.message
            ?.take(MAX_ERROR_CHARACTERS)
            .orEmpty()
            .ifBlank { "unknown error" }

    private fun JsonPrimitive.booleanOrNumber(): Any =
        when {
            content.equals("true", ignoreCase = true) -> true
            content.equals("false", ignoreCase = true) -> false
            else -> content.toDoubleOrNull() ?: content
        }

    private companion object {
        const val JAVASCRIPT_TOOL_ID = "javascript:execute"
        const val MAX_ERROR_CHARACTERS = 500
    }
}
