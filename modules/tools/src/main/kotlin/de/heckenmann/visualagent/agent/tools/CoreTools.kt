
package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsPort
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsUpdate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool that exposes safe UI/session settings to the model.
 *
 * API keys are reported only as configured/not configured and are never included
 * in the returned content.
 *
 * Use cases: UC-0000061.
 */
class UiTool(
    private val settings: ToolSettingsPort,
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
                settings.update(
                    ToolSettingsUpdate(
                        fontSize = input.int("fontSize")?.coerceIn(10, 24),
                        provider = input.string("provider"),
                        model = input.string("model"),
                        openAiBaseUrl = input.string("openAiBaseUrl"),
                        streamingEnabled = input.boolean("streamingEnabled"),
                        thinkingEnabled = input.boolean("thinkingEnabled"),
                    ),
                )
            }
            "get" -> Unit
            else -> return failure("ui", "Unsupported ui action")
        }
        val current = settings.read()
        return success(
            "ui",
            """
            Current UI Settings:
              Font size: ${current.fontSize}px
              Provider: ${current.provider}
              Model: ${current.model}
              OpenAI Base URL: ${current.openAiBaseUrl}
              OpenAI API key configured: ${current.openAiApiKeyConfigured}
              Streaming: ${current.streamingEnabled}
              Thinking: ${current.thinkingEnabled}
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
class ContextTool(
    private val settings: ToolSettingsPort,
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
                val current = settings.read()
                appendLine("Provider: ${current.provider}")
                appendLine("Model: ${current.model}")
                appendLine("OpenAI Base URL: ${current.openAiBaseUrl}")
                appendLine("OpenAI API key configured: ${current.openAiApiKeyConfigured}")
                context.entries.sortedBy { it.key }.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
            }.trim(),
        )
}

/**
 * Tool that lets the model inspect and mutate persisted todo records.
 */
class TodosTool(
    private val todos: TodoToolPort,
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
            "complete" -> updateStatus(input, "COMPLETED")
            "cancel" -> updateStatus(input, "CANCELLED")
            "remove" -> removeTodo(input)
            "reorder" -> reorderTodos(input)
            "get-result" -> getTodoResult(input)
            else -> failure("todos", "Unsupported todos action")
        }
    }

    private fun listTodos(): ToolResult {
        val rows = todos.list()
        if (rows.isEmpty()) return success("todos", "No todos.")
        val text =
            rows.joinToString("\n") {
                "- [${it.status}] ${it.description} (id=${it.id}, position=${it.position}, assigned=${it.assignedAgentId ?: "none"})"
            }
        return success("todos", text)
    }

    private fun countTodos(): ToolResult {
        val rows = todos.list()
        val pending = rows.count { it.status == "PENDING" }
        val inProgress = rows.count { it.status == "IN_PROGRESS" }
        val completed = rows.count { it.status == "COMPLETED" }
        val cancelled = rows.count { it.status == "CANCELLED" }
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
        return success("todos", "Added todo ${todos.add(description, assignedAgentId)}")
    }

    private fun updateTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        todos.list().firstOrNull { it.id == id } ?: return failure("todos", "Todo not found")
        val newAssignedAgentId = input.string("assignedAgentId")
        if (newAssignedAgentId != null && !agentExists(newAssignedAgentId)) {
            return failure(
                "todos",
                "assignedAgentId must reference an existing sub-agent",
            )
        }
        todos.update(id, input.string("description"), newAssignedAgentId, input.string("status")?.uppercase())
        return success("todos", "Updated todo $id")
    }

    private fun updateStatus(
        input: JsonObject,
        status: String,
    ): ToolResult {
        val id = input.requiredString("id")
        todos.list().firstOrNull { it.id == id } ?: return failure("todos", "Todo not found")
        val success = todos.setStatus(id, status)
        return if (success) {
            success("todos", "Set todo $id to $status")
        } else {
            failure("todos", "Todo not found or invalid status transition")
        }
    }

    private fun removeTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        return if (todos.remove(id)) {
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
                val ordered = todos.list().sortedBy { it.position }.map { it.id }
                val beforeIndex =
                    ordered.indexOf(beforeId).takeIf { it != -1 }
                        ?: return failure("todos", "Reference todo not found")
                val fromIndex = ordered.indexOf(id)
                if (fromIndex == -1) return failure("todos", "Todo not found")
                if (fromIndex < beforeIndex) beforeIndex - 1 else beforeIndex
            }
        return if (todos.moveToPosition(id, targetPosition)) {
            success("todos", "Reordered todo $id to position $targetPosition")
        } else {
            failure("todos", "Todo not found")
        }
    }

    private fun getTodoResult(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        val result = todos.result(id) ?: return failure("todos", "No result available for todo $id")
        val summary = extractSummary(result)
        return success("todos", "Result for todo $id:\n$summary")
    }

    private fun agentExists(agentId: String): Boolean = todos.agentExists(agentId)

    private fun extractSummary(content: String): String {
        val parsed = runCatching { parseObject(content) }.getOrNull()
        if (parsed != null) {
            val summary = parsed["summary"]?.jsonPrimitive?.contentOrNull
            if (!summary.isNullOrBlank()) return summary
        }
        return content.take(2000)
    }
}
