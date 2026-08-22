
package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool that lets the model inspect and mutate persisted todo records.
 */
@AgentTool
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
                    "Sub-agents have read-only access; todo lifecycle changes are controlled by the main agent and orchestrator. " +
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
                    "- start: {\"action\":\"start\",\"id\":\"...\"}. Starts one pending or cancelled todo.\n" +
                    "- start-all: {\"action\":\"start-all\"}. Starts all pending and cancelled todos.\n" +
                    "- stop: {\"action\":\"stop\",\"id\":\"...\"}. Stops one pending or in-progress todo.\n" +
                    "- stop-all: {\"action\":\"stop-all\"}. Stops all pending and in-progress todos.\n" +
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
        val action = input.string("action") ?: "list"
        if (isSubAgentRequest(context) && action !in SUB_AGENT_READ_ONLY_ACTIONS) {
            return failure(
                "todos",
                "Sub-agents may only read todos; lifecycle changes are controlled by the main agent.",
            )
        }
        return when (action) {
            "list" -> listTodos()
            "count" -> countTodos()
            "add" -> addTodo(input)
            "update" -> updateTodo(input)
            "complete" -> updateStatus(input, "COMPLETED")
            "cancel" -> updateStatus(input, "CANCELLED")
            "start" -> startTodo(input)
            "start-all" -> startAllTodos()
            "stop" -> stopTodo(input)
            "stop-all" -> stopAllTodos()
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
        val creation = todos.addIfAbsent(description, assignedAgentId)
        if (!creation.created) {
            val existing = creation.todo
            return success(
                "todos",
                "Todo already exists: ${existing.id} [${existing.status}] " +
                    "${existing.description}. Reuse this todo instead of creating a duplicate.",
            )
        }
        return success("todos", "Added todo ${creation.todo.id}")
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

    private fun startTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        if (todos.list().none { it.id == id }) return failure("todos", "Todo not found")
        return if (todos.start(id)) success("todos", "Started todo $id") else failure("todos", "Todo cannot be started")
    }

    private fun startAllTodos(): ToolResult = success("todos", "Started ${todos.startAll()} todos")

    private fun stopTodo(input: JsonObject): ToolResult {
        val id = input.requiredString("id")
        if (todos.list().none { it.id == id }) return failure("todos", "Todo not found")
        return if (todos.stop(id)) success("todos", "Stopped todo $id") else failure("todos", "Todo cannot be stopped")
    }

    private fun stopAllTodos(): ToolResult = success("todos", "Stopped ${todos.stopAll()} todos")

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

    private fun isSubAgentRequest(context: Map<String, Any>): Boolean = context["agentId"]?.toString()?.isNotBlank() == true

    private companion object {
        val SUB_AGENT_READ_ONLY_ACTIONS = setOf("list", "count", "get-result")
    }

    private fun extractSummary(content: String): String {
        val parsed = runCatching { parseObject(content) }.getOrNull()
        if (parsed != null) {
            val summary = parsed["summary"]?.jsonPrimitive?.contentOrNull
            if (!summary.isNullOrBlank()) return summary
        }
        return content.take(2000)
    }
}
