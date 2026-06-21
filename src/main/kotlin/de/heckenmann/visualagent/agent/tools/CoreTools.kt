package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component

/**
 * Tool that exposes safe UI/session settings to the model.
 *
 * API keys are reported only as configured/not configured and are never included
 * in the returned content.
 *
 * Use cases: UC-0000061.
 */
@Component
class UiTool(
    private val providerCatalog: ProviderCatalogService,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("ui"),
            name = ToolId("ui").toFunctionName(),
            description = "Read or update Visual Agent UI settings such as theme, font size, model, and session options.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        when (input.string("action") ?: "get") {
            "set" -> {
                input.string("theme")?.let { AppConfig.instance.theme = it }
                input.int("fontSize")?.let { AppConfig.instance.fontSize = it.coerceIn(10, 24) }
                input.string("provider")?.let(providerCatalog::setActiveProvider)
                input.string("model")?.let { model ->
                    val provider = providerCatalog.getProvider(providerCatalog.activeProviderId())
                    if (provider != null) providerCatalog.saveProvider(provider.copy(defaultModel = model))
                }
                input.string("openAiBaseUrl")?.let { AppConfig.instance.openAiBaseUrl = it }
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
              Provider: ${providerCatalog.activeProviderId()}
              Model: ${providerCatalog.getProvider(providerCatalog.activeProviderId())?.defaultModel.orEmpty()}
              OpenAI Base URL: ${AppConfig.instance.openAiBaseUrl}
              OpenAI API key configured: ${AppConfig.instance.openAiApiKey.isNotBlank()}
              Streaming: ${AppConfig.instance.streamingEnabled}
              Thinking: ${AppConfig.instance.thinkingEnabled}
            Available themes: Dracula, Primer Dark, Primer Light, Nord Dark, Nord Light, Cupertino Dark, Cupertino Light
            Font size range: 10-24
            """.trimIndent(),
        )
    }
}

/**
 * Tool that returns the workspace root used for file and terminal operations.
 *
 * Use cases: UC-0000060.
 */
@Component
class PwdTool : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("pwd"),
            name = ToolId("pwd").toFunctionName(),
            description = "Return the current Visual Agent workspace directory.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = success("pwd", workspaceRoot().toString())
}

/**
 * Tool that summarizes request metadata, workspace state, and active provider selection.
 *
 * Use cases: UC-0000059.
 */
@Component
class ContextTool(
    private val providerCatalog: ProviderCatalogService? = null,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("context"),
            name = ToolId("context").toFunctionName(),
            description = "Return current model, session, agent, workspace, and enabled tool context.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult =
        success(
            "context",
            buildString {
                appendLine("Workspace: ${workspaceRoot()}")
                val activeProvider = providerCatalog?.activeProviderId() ?: AppConfig.instance.llmProvider
                appendLine("Provider: $activeProvider")
                appendLine("Model: ${providerCatalog?.getProvider(activeProvider)?.defaultModel ?: AppConfig.instance.activeModel()}")
                appendLine("OpenAI Base URL: ${AppConfig.instance.openAiBaseUrl}")
                appendLine("OpenAI API key configured: ${AppConfig.instance.openAiApiKey.isNotBlank()}")
                appendLine("Theme: ${AppConfig.instance.theme}")
                context.entries.sortedBy { it.key }.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
            }.trim(),
        )
}

/**
 * Tool that lets the model inspect and mutate persisted todo records.
 */
@Component
class TodosTool(
    private val todoStore: TodoStore,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("todos"),
            name = ToolId("todos").toFunctionName(),
            description =
                "Manage todos. Actions: list, count, add, update, complete, cancel, remove. " +
                    "Input: {\"action\":\"list|count|add|update|complete|cancel|remove\", ...}.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        return when (input.string("action") ?: "list") {
            "list" -> listTodos()
            "count" -> countTodos()
            "add" -> addTodo(input)
            "update" -> updateTodo(input)
            "complete" -> updateStatus(input, TodoStatus.COMPLETED)
            "cancel" -> updateStatus(input, TodoStatus.CANCELLED)
            "remove" -> removeTodo(input)
            else -> failure("todos", "Unsupported todos action")
        }
    }

    private fun listTodos(): ToolResult {
        val rows = todoStore.listTodos()
        if (rows.isEmpty()) return success("todos", "No todos.")
        val text =
            rows.joinToString("\n") {
                "- [${it.status}] ${it.description} (id=${it.id}, priority=${it.priority}, assigned=${it.assignedAgentId ?: "none"})"
            }
        return success("todos", text)
    }

    private fun countTodos(): ToolResult {
        val rows = todoStore.listTodos()
        val pending = rows.count { it.status == TodoStatus.PENDING }
        val inProgress = rows.count { it.status == TodoStatus.IN_PROGRESS }
        val completed = rows.count { it.status == TodoStatus.COMPLETED }
        val cancelled = rows.count { it.status == TodoStatus.CANCELLED }
        val total = rows.size
        return success(
            "todos",
            "total=$total, open=$pending, in_progress=$inProgress, completed=$completed, cancelled=$cancelled",
        )
    }

    private fun addTodo(input: JsonObject): ToolResult {
        val todo =
            Todo(
                id =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                description = input.requiredString("description"),
                status = TodoStatus.PENDING,
                priority = parsePriority(input.string("priority")),
            )
        todoStore.saveTodo(todo)
        return success("todos", "Added todo ${todo.id}")
    }

    private fun updateTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        val existing = todoStore.listTodos().firstOrNull { it.id == id } ?: return failure("todos", "Todo not found")
        val updated =
            existing
                .copy(
                    description = input.string("description") ?: existing.description,
                    priority = parsePriority(input.string("priority"), existing.priority),
                    assignedAgentId = input.string("assignedAgentId") ?: existing.assignedAgentId,
                ).also {
                    it.status =
                        input.string("status")?.let { s -> runCatching { TodoStatus.valueOf(s.uppercase()) }.getOrNull() }
                            ?: existing.status
                    it.completedAt = existing.completedAt
                }
        todoStore.saveTodo(updated)
        return success("todos", "Updated todo $id")
    }

    private fun updateStatus(
        input: JsonObject,
        status: TodoStatus,
    ): ToolResult {
        val id = input.requiredString("id")
        val existing = todoStore.listTodos().firstOrNull { it.id == id } ?: return failure("todos", "Todo not found")
        val updated =
            existing.copy().also {
                it.status = status
                if (status == TodoStatus.COMPLETED) {
                    it.completedAt = java.time.Instant.now()
                }
            }
        todoStore.saveTodo(updated)
        return success("todos", "Set todo $id to $status")
    }

    private fun removeTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        todoStore.deleteTodo(id)
        return success("todos", "Removed todo $id")
    }

    private fun parsePriority(
        value: String?,
        fallback: TodoPriority = TodoPriority.MEDIUM,
    ): TodoPriority = value?.let { runCatching { TodoPriority.valueOf(it.uppercase()) }.getOrNull() } ?: fallback
}
