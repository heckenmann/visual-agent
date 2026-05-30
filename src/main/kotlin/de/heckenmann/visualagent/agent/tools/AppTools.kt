package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.KnowledgeDb
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val json = Json { ignoreUnknownKeys = true }

private const val STRING_SCHEMA = """{"type":"object","additionalProperties":true}"""

/**
 * Tool for reading and updating safe UI settings.
 */
@Component
class UiTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("ui"),
        name = ToolId("ui").toFunctionName(),
        description = "Read or update Visual Agent UI settings such as theme, font size, model, and session options.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
        val input = parseObject(inputJson)
        when (input.string("action") ?: "get") {
            "set" -> {
                input.string("theme")?.let { AppConfig.instance.theme = it }
                input.int("fontSize")?.let { AppConfig.instance.fontSize = it.coerceIn(10, 24) }
                input.string("model")?.let { AppConfig.instance.ollamaModel = it }
                input.boolean("streamingEnabled")?.let { AppConfig.instance.streamingEnabled = it }
                input.boolean("thinkingEnabled")?.let { AppConfig.instance.thinkingEnabled = it }
                AppConfig.instance.save()
            }
            "get" -> Unit
            else -> return failure("ui", "Unsupported ui action")
        }
        return success(
            "ui",
            """
            Current UI Settings:
              Theme: ${AppConfig.instance.theme}
              Font size: ${AppConfig.instance.fontSize}px
              Model: ${AppConfig.instance.ollamaModel}
              Streaming: ${AppConfig.instance.streamingEnabled}
              Thinking: ${AppConfig.instance.thinkingEnabled}
            Available themes: Dracula, Primer Dark, Primer Light, Nord Dark, Nord Light, Cupertino Dark, Cupertino Light
            Font size range: 10-24
            """.trimIndent(),
        )
    }
}

/**
 * Tool that returns the current workspace path.
 */
@Component
class PwdTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("pwd"),
        name = ToolId("pwd").toFunctionName(),
        description = "Return the current Visual Agent workspace directory.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult =
        success("pwd", workspaceRoot().toString())
}

/**
 * Tool that returns request and application context.
 */
@Component
class ContextTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("context"),
        name = ToolId("context").toFunctionName(),
        description = "Return current model, session, agent, workspace, and enabled tool context.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult =
        success(
            "context",
            buildString {
                appendLine("Workspace: ${workspaceRoot()}")
                appendLine("Model: ${AppConfig.instance.ollamaModel}")
                appendLine("Theme: ${AppConfig.instance.theme}")
                context.entries.sortedBy { it.key }.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
            }.trim(),
        )
}

/**
 * Tool for listing and managing todo items stored in the database.
 */
@Component
class TodosTool(
    private val knowledgeDb: KnowledgeDb,
) : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("todos"),
        name = ToolId("todos").toFunctionName(),
        description = "Manage todos. Actions: list, add, update, complete, cancel, remove. Input: {\"action\":\"list|add|update|complete|cancel|remove\", ...}.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
        val input = parseObject(inputJson)
        return when (input.string("action") ?: "list") {
            "list" -> listTodos()
            "add" -> addTodo(input)
            "update" -> updateTodo(input)
            "complete" -> updateStatus(input, TodoStatus.COMPLETED)
            "cancel" -> updateStatus(input, TodoStatus.CANCELLED)
            "remove" -> removeTodo(input)
            else -> failure("todos", "Unsupported todos action")
        }
    }

    private fun listTodos(): ToolResult {
        val rows = knowledgeDb.listTodos()
        if (rows.isEmpty()) return success("todos", "No todos.")
        val text = rows.joinToString("\n") {
            "- [${it.status}] ${it.description} (id=${it.id}, priority=${it.priority}, assigned=${it.assignedAgentId ?: "none"})"
        }
        return success("todos", text)
    }

    private fun addTodo(input: JsonObject): ToolResult {
        val todo = Todo(
            id = java.util.UUID.randomUUID().toString(),
            description = input.requiredString("description"),
            status = TodoStatus.PENDING,
            priority = parsePriority(input.string("priority")),
        )
        knowledgeDb.saveTodo(todo)
        return success("todos", "Added todo ${todo.id}")
    }

    private fun updateTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        val existing = knowledgeDb.listTodos().firstOrNull { it.id == id } ?: return failure("todos", "Todo not found")
        val updated = existing.copy(
            description = input.string("description") ?: existing.description,
            priority = parsePriority(input.string("priority"), existing.priority),
            assignedAgentId = input.string("assignedAgentId") ?: existing.assignedAgentId,
        ).also {
            it.status = input.string("status")?.let { s -> runCatching { TodoStatus.valueOf(s.uppercase()) }.getOrNull() } ?: existing.status
            it.completedAt = existing.completedAt
        }
        knowledgeDb.saveTodo(updated)
        return success("todos", "Updated todo $id")
    }

    private fun updateStatus(input: JsonObject, status: TodoStatus): ToolResult {
        val id = input.requiredString("id")
        val existing = knowledgeDb.listTodos().firstOrNull { it.id == id } ?: return failure("todos", "Todo not found")
        val updated = existing.copy().also {
            it.status = status
            if (status == TodoStatus.COMPLETED) {
                it.completedAt = java.time.Instant.now()
            }
        }
        knowledgeDb.saveTodo(updated)
        return success("todos", "Set todo $id to $status")
    }

    private fun removeTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        knowledgeDb.deleteTodo(id)
        return success("todos", "Removed todo $id")
    }

    private fun parsePriority(value: String?, fallback: TodoPriority = TodoPriority.MEDIUM): TodoPriority =
        value?.let { runCatching { TodoPriority.valueOf(it.uppercase()) }.getOrNull() } ?: fallback
}

/**
 * Tool for reading a file under the workspace root.
 */
@Component
class FileReadTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("file:read"),
        name = ToolId("file:read").toFunctionName(),
        description = "Read a UTF-8 text file under the workspace root. Input: {\"path\":\"relative/path\"}.",
        inputSchema = pathSchema(),
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
        val path = when (val resolved = resolveWorkspacePathOrFailure("file:read", parseObject(inputJson).requiredString("path"))) {
            is PathResolution.Failure -> return resolved.result
            is PathResolution.Success -> resolved.path
        }
        if (!path.exists() || path.isDirectory()) return failure("file:read", "File does not exist or is a directory")
        return success("file:read", path.readText(StandardCharsets.UTF_8))
    }
}

/**
 * Tool for listing files under the workspace root.
 */
@Component
class FileListTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("file:list"),
        name = ToolId("file:list").toFunctionName(),
        description = "List files under a workspace directory. Input: {\"path\":\"relative/path\"}.",
        inputSchema = pathSchema(),
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
        val path = when (val resolved = resolveWorkspacePathOrFailure("file:list", parseObject(inputJson).string("path") ?: ".")) {
            is PathResolution.Failure -> return resolved.result
            is PathResolution.Success -> resolved.path
        }
        if (!path.exists() || !path.isDirectory()) return failure("file:list", "Directory does not exist")
        val content = Files.list(path).use { stream ->
            stream.sorted().map { if (it.isDirectory()) "${it.name}/" else it.name }.toList().joinToString("\n")
        }
        return success("file:list", content)
    }
}

/**
 * Tool for globbing files under the workspace root.
 */
@Component
class FileGlobTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("file:glob"),
        name = ToolId("file:glob").toFunctionName(),
        description = "Find files by glob under the workspace root. Input: {\"pattern\":\"**/*.kt\"}.",
        inputSchema = requiredStringSchema("pattern"),
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
        val pattern = parseObject(inputJson).requiredString("pattern")
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        val root = workspaceRoot()
        val matches = Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .map { root.relativize(it).toString() }
                .filter { matcher.matches(Path.of(it)) }
                .limit(500)
                .toList()
        }
        return success("file:glob", matches.joinToString("\n"))
    }
}

/**
 * Tool for searching text in files under the workspace root.
 */
@Component
class FileGrepTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("file:grep"),
        name = ToolId("file:grep").toFunctionName(),
        description = "Search UTF-8 files under the workspace root. Input: {\"query\":\"text\",\"path\":\"optional/path\"}.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
        val input = parseObject(inputJson)
        val query = input.requiredString("query")
        val root = when (val resolved = resolveWorkspacePathOrFailure("file:grep", input.string("path") ?: ".")) {
            is PathResolution.Failure -> return resolved.result
            is PathResolution.Success -> resolved.path
        }
        if (!root.exists()) return failure("file:grep", "Search path does not exist")
        val lines = mutableListOf<String>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }.limit(1000).forEach { file ->
                runCatching {
                    Files.readAllLines(file, StandardCharsets.UTF_8).forEachIndexed { index, line ->
                        if (line.contains(query, ignoreCase = true) && lines.size < 200) {
                            lines += "${workspaceRoot().relativize(file)}:${index + 1}: $line"
                        }
                    }
                }
            }
        }
        return success("file:grep", lines.joinToString("\n"))
    }
}

/**
 * Tool for writing UTF-8 text files under the workspace root.
 */
@Component
class FileWriteTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("file:write"),
        name = ToolId("file:write").toFunctionName(),
        description = "Write a UTF-8 text file under the workspace root. Input: {\"path\":\"relative/path\",\"content\":\"text\"}.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
        val input = parseObject(inputJson)
        val path = when (val resolved = resolveWorkspacePathOrFailure("file:write", input.requiredString("path"))) {
            is PathResolution.Failure -> return resolved.result
            is PathResolution.Success -> resolved.path
        }
        path.parent?.createDirectories()
        path.writeText(input.requiredString("content"), StandardCharsets.UTF_8)
        return success("file:write", "Wrote ${workspaceRoot().relativize(path)}")
    }
}

/**
 * Tool for replacing text in UTF-8 files under the workspace root.
 */
@Component
class FileEditTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("file:edit"),
        name = ToolId("file:edit").toFunctionName(),
        description = "Replace text in a UTF-8 file. Input: {\"path\":\"relative/path\",\"oldText\":\"old\",\"newText\":\"new\"}.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
        val input = parseObject(inputJson)
        val path = when (val resolved = resolveWorkspacePathOrFailure("file:edit", input.requiredString("path"))) {
            is PathResolution.Failure -> return resolved.result
            is PathResolution.Success -> resolved.path
        }
        if (!path.exists() || path.isDirectory()) return failure("file:edit", "File does not exist or is a directory")
        val oldText = input.requiredString("oldText")
        val current = path.readText(StandardCharsets.UTF_8)
        if (!current.contains(oldText)) return failure("file:edit", "oldText not found")
        path.writeText(current.replace(oldText, input.requiredString("newText")), StandardCharsets.UTF_8)
        return success("file:edit", "Edited ${workspaceRoot().relativize(path)}")
    }
}

/**
 * Tool for running bounded shell commands in the workspace.
 */
@Component
class TerminalTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("terminal"),
        name = ToolId("terminal").toFunctionName(),
        description = "Run a non-interactive shell command in the workspace. Input: {\"command\":\"pwd\",\"timeoutSeconds\":10}.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult {
        val input = parseObject(inputJson)
        val command = input.requiredString("command")
        val timeout = (input.int("timeoutSeconds") ?: 10).coerceIn(1, 30)
        val process = ProcessBuilder("zsh", "-lc", command)
            .directory(workspaceRoot().toFile())
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return failure("terminal", "Command timed out after ${timeout}s")
        }
        val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8).take(8000)
        return ToolResult("terminal", process.exitValue() == 0, output, if (process.exitValue() == 0) null else "Exit ${process.exitValue()}")
    }
}

/**
 * Placeholder browser tool until an in-app browser backend is wired to the application.
 */
@Component
class BrowserTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("browser"),
        name = ToolId("browser").toFunctionName(),
        description = "Browser automation interface. Currently reports unavailable when no backend is configured.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult =
        failure("browser", "Browser backend is not configured in this application runtime")
}

/**
 * Placeholder search tool until a search backend is wired to the application.
 */
@Component
class SearchTool : VisualAgentTool {
    override val definition = ToolDefinition(
        id = ToolId("search"),
        name = ToolId("search").toFunctionName(),
        description = "Search interface. Currently reports unavailable when no backend is configured.",
        inputSchema = STRING_SCHEMA,
    )

    override fun execute(inputJson: String, context: Map<String, Any>): ToolResult =
        failure("search", "Search backend is not configured in this application runtime")
}

private fun parseObject(inputJson: String): JsonObject =
    runCatching { json.parseToJsonElement(inputJson).jsonObject }.getOrElse { JsonObject(emptyMap()) }

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.requiredString(key: String): String =
    string(key) ?: throw IllegalArgumentException("Missing required field '$key'")

private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

private fun workspaceRoot(): Path = Path.of(System.getProperty("user.dir")).absolute().normalize()

private fun resolveWorkspacePath(path: String): Path {
    val resolved = workspaceRoot().resolve(path).normalize()
    require(resolved.startsWith(workspaceRoot())) { "Path escapes workspace root" }
    return resolved
}

private fun resolveWorkspacePathOrFailure(toolId: String, path: String): PathResolution =
    runCatching { PathResolution.Success(resolveWorkspacePath(path)) }
        .getOrElse { PathResolution.Failure(failure(toolId, it.message ?: "Invalid path")) }

private sealed interface PathResolution {
    data class Success(val path: Path) : PathResolution
    data class Failure(val result: ToolResult) : PathResolution
}

private fun success(toolId: String, content: String): ToolResult = ToolResult(toolId, true, content)

private fun failure(toolId: String, error: String): ToolResult = ToolResult(toolId, false, "", error)

private fun pathSchema(): String = requiredStringSchema("path")

private fun requiredStringSchema(name: String): String =
    """{"type":"object","properties":{"$name":{"type":"string"}},"required":["$name"]}"""
