
package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
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
            description =
                "Read or update Visual Agent UI settings. Actions: get, set. " +
                    "Input: {\"action\":\"get|set\",\"fontSize\":14,\"provider\":\"ollama\"," +
                    "\"model\":\"llama3\",\"streamingEnabled\":true,\"thinkingEnabled\":false}. " +
                    "Font size range: 10-24. API keys are reported as configured/not configured only.",
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
            description =
                "Return the current Visual Agent workspace directory. " +
                    "No input parameters required. " +
                    "Input: {}.",
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
            description =
                "Return current model, session, agent, workspace, and enabled tool context. " +
                    "No input parameters required. " +
                    "Input: {}.",
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
                "Manage the task plan (work items / to-do list). " +
                    "This tool is ONLY for tracking work items — NEVER use it to store code, data, file contents, or results. " +
                    "Use file:write to save code and data to files, and use todos only to describe what work needs to be done.\n" +
                    "Actions and their required input parameters:\n" +
                    "- list: no parameters. Returns all todos with status, description, id, position, assigned agent.\n" +
                    "- count: no parameters. Returns counts per status.\n" +
                    "- add: {\"action\":\"add\",\"description\":\"task description here\",\"assignedAgentId\":\"...\"}. " +
                    "Creates a new work item. The description must be a short task description, NOT code or data. " +
                    "assignedAgentId is required and must reference an existing sub-agent.\n" +
                    "- update: {\"action\":\"update\",\"id\":\"...\",\"description\":\"...\"," +
                    "\"assignedAgentId\":\"...\",\"status\":\"PENDING|IN_PROGRESS|COMPLETED|CANCELLED\"}. " +
                    "All fields except id are optional.\n" +
                    "- complete: {\"action\":\"complete\",\"id\":\"...\"}. Marks a todo as COMPLETED.\n" +
                    "- cancel: {\"action\":\"cancel\",\"id\":\"...\"}. Marks a todo as CANCELLED.\n" +
                    "- remove: {\"action\":\"remove\",\"id\":\"...\"}. Deletes a todo permanently.\n" +
                    "- reorder: {\"action\":\"reorder\",\"id\":\"...\",\"position\":0}. " +
                    "Moves a todo to a new position (0 = first).\n" +
                    "- get-result: {\"action\":\"get-result\",\"id\":\"...\"}. " +
                    "Reads the stored result summary for a completed/cancelled todo.",
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
        val description = input.requiredString("description")
        val todo = agentManager.todoManager.add(description, assignedAgentId)
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
        input.string("description")?.let { agentManager.todoManager.update(id, it) }
        newAssignedAgentId?.let { agentManager.todoManager.updateAssignedAgent(id, it) }
        input.string("status")?.let { s ->
            runCatching { TodoStatus.valueOf(s.uppercase()) }.getOrNull()?.let { status ->
                agentManager.todoManager.updateStatus(id, status)
            }
        }
        return success("todos", "Updated todo $id")
    }

    private fun updateStatus(
        input: JsonObject,
        status: TodoStatus,
    ): ToolResult {
        val id = input.requiredString("id")
        val existing = todoStore.listTodos().firstOrNull { it.id == id } ?: return failure("todos", "Todo not found")
        val success =
            when (status) {
                TodoStatus.COMPLETED -> agentManager.todoManager.completeTodo(id)
                TodoStatus.CANCELLED -> agentManager.todoManager.cancelTodo(id)
                else -> agentManager.todoManager.updateStatus(id, status)
            }
        return if (success) {
            success("todos", "Set todo $id to $status")
        } else {
            failure("todos", "Todo not found or invalid status transition")
        }
    }

    private fun removeTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        return if (agentManager.todoManager.remove(id)) {
            success("todos", "Removed todo $id")
        } else {
            failure("todos", "Todo not found")
        }
    }

    private fun reorderTodos(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        val targetPosition =
            input.int("position") ?: run {
                val beforeId = input.string("before") ?: return failure("todos", "Reorder requires 'position' or 'before'")
                val todos = todoStore.listTodos()
                val ordered = todos.sortedBy { it.position }.map { it.id }
                val beforeIndex =
                    ordered.indexOf(beforeId).takeIf { it != -1 }
                        ?: return failure("todos", "Reference todo not found")
                val fromIndex = ordered.indexOf(id)
                if (fromIndex == -1) return failure("todos", "Todo not found")
                if (fromIndex < beforeIndex) beforeIndex - 1 else beforeIndex
            }
        return if (agentManager.todoManager.moveToPosition(id, targetPosition)) {
            success("todos", "Reordered todo $id to position $targetPosition")
        } else {
            failure("todos", "Todo not found")
        }
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
