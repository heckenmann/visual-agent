package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.context.annotation.Lazy
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
    private val appConfig: AppConfigBean = AppConfigBean(),
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("ui"),
            name = ToolId("ui").toFunctionName(),
            description = "Read or update Visual Agent UI settings such as font size, model, and session options.",
            inputSchema = STRING_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        when (input.string("action") ?: "get") {
            "set" -> {
                input.int("fontSize")?.let { appConfig.fontSize = it.coerceIn(10, 24) }
                input.string("provider")?.let(providerCatalog::setActiveProvider)
                input.string("model")?.let { model ->
                    val provider = providerCatalog.getProvider(providerCatalog.activeProviderId())
                    if (provider != null) providerCatalog.saveProvider(provider.copy(defaultModel = model))
                }
                input.string("openAiBaseUrl")?.let { appConfig.openAiBaseUrl = it }
                input.boolean("streamingEnabled")?.let { appConfig.streamingEnabled = it }
                input.boolean("thinkingEnabled")?.let { appConfig.thinkingEnabled = it }
                appConfig.save()
            }
            "get" -> Unit
            else -> return failure("ui", "Unsupported ui action")
        }
        return success(
            "ui",
            """
            Current UI Settings:
              Font size: ${appConfig.fontSize}px
              Provider: ${providerCatalog.activeProviderId()}
              Model: ${providerCatalog.getProvider(providerCatalog.activeProviderId())?.defaultModel.orEmpty()}
              OpenAI Base URL: ${appConfig.openAiBaseUrl}
              OpenAI API key configured: ${appConfig.openAiApiKey.isNotBlank()}
              Streaming: ${appConfig.streamingEnabled}
              Thinking: ${appConfig.thinkingEnabled}
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
    private val appConfig: AppConfigBean = AppConfigBean(),
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
                val activeProvider = providerCatalog?.activeProviderId() ?: appConfig.llmProvider
                appendLine("Provider: $activeProvider")
                appendLine("Model: ${providerCatalog?.getProvider(activeProvider)?.defaultModel ?: appConfig.activeModel()}")
                appendLine("OpenAI Base URL: ${appConfig.openAiBaseUrl}")
                appendLine("OpenAI API key configured: ${appConfig.openAiApiKey.isNotBlank()}")
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
    private val memoryStore: MemoryStore,
    @param:Lazy private val agentManager: AgentManager,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("todos"),
            name = ToolId("todos").toFunctionName(),
            description =
                "Manage todos. Actions: list, count, add, update, complete, cancel, remove, reorder, get-result. " +
                    "Input: {\"action\":\"list|count|add|update|complete|cancel|remove|reorder|get-result\", ...}. " +
                    "Every new todo must include assignedAgentId referencing an existing sub-agent. " +
                    "The first pending todo is the next one to process; use reorder to change what is next. " +
                    "Use get-result to read the stored result summary for a completed/cancelled todo.",
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
            "reorder" -> reorderTodos(input)
            "get-result" -> getTodoResult(input)
            else -> failure("todos", "Unsupported todos action")
        }
    }

    private fun listTodos(): ToolResult {
        val rows = todoStore.listTodos()
        if (rows.isEmpty()) return success("todos", "No todos.")
        val text =
            rows.joinToString("\n") {
                "- [${it.status}] ${it.description} (id=${it.id}, position=${it.position}, assigned=${it.assignedAgentId ?: "none"})"
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
        val assignedAgentId =
            input.string("assignedAgentId")
                ?: return failure(
                    "todos",
                    "assignedAgentId is required and must reference an existing sub-agent",
                )
        if (!agentExists(assignedAgentId)) {
            return failure(
                "todos",
                "assignedAgentId is required and must reference an existing sub-agent",
            )
        }
        val todo =
            Todo(
                id =
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                description = input.requiredString("description"),
                status = TodoStatus.PENDING,
                position = todoStore.listTodos().maxOfOrNull { it.position }?.plus(1) ?: 0,
                assignedAgentId = assignedAgentId,
            )
        todoStore.saveTodo(todo)
        return success("todos", "Added todo ${todo.id}")
    }

    private fun updateTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        val existing = todoStore.listTodos().firstOrNull { it.id == id } ?: return failure("todos", "Todo not found")
        val newAssignedAgentId = input.string("assignedAgentId")
        if (newAssignedAgentId != null && !agentExists(newAssignedAgentId)) {
            return failure(
                "todos",
                "assignedAgentId must reference an existing sub-agent",
            )
        }
        val updated =
            existing
                .copy(
                    description = input.string("description") ?: existing.description,
                    assignedAgentId = newAssignedAgentId ?: existing.assignedAgentId,
                ).also {
                    it.status =
                        input.string("status")?.let { s -> runCatching { TodoStatus.valueOf(s.uppercase()) }.getOrNull() }
                            ?: existing.status
                    it.position = existing.position
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

    private fun reorderTodos(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        val todos = todoStore.listTodos()
        val ordered = todos.sortedBy { it.position }.map { it.id }
        val fromIndex = ordered.indexOf(id)
        if (fromIndex == -1) return failure("todos", "Todo not found")
        val targetPosition =
            input.int("position") ?: run {
                val beforeId = input.string("before") ?: return failure("todos", "Reorder requires 'position' or 'before'")
                val beforeIndex =
                    ordered.indexOf(beforeId).takeIf { it != -1 }
                        ?: return failure("todos", "Reference todo not found")
                if (fromIndex < beforeIndex) beforeIndex - 1 else beforeIndex
            }
        val safeTarget = targetPosition.coerceIn(0, ordered.lastIndex)
        val reordered =
            ordered.toMutableList().apply {
                add(safeTarget, removeAt(fromIndex))
            }
        reordered.forEachIndexed { index, todoId ->
            val todo = todos.first { it.id == todoId }
            todoStore.saveTodo(todo.copy(position = index))
        }
        return success("todos", "Reordered todo $id to position $targetPosition")
    }

    private fun getTodoResult(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        val results = memoryStore.searchMemories("todo:$id", limit = 1)
        val result =
            results.firstOrNull()
                ?: return failure("todos", "No result available for todo $id")
        val summary = extractSummary(result.content)
        return success("todos", "Result for todo $id:\n$summary")
    }

    private fun agentExists(agentId: String): Boolean = agentManager.getSubAgent(agentId) != null

    private fun extractSummary(content: String): String {
        val parsed = runCatching { parseObject(content) }.getOrNull()
        if (parsed != null) {
            val summary = parsed["summary"]?.jsonPrimitive?.contentOrNull
            if (!summary.isNullOrBlank()) return summary
        }
        return content.take(2000)
    }
}
