package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.mapToolNames
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Bounded contract and attempt detail actions, separate from list summaries. */
internal class TodoContractActions(
    private val todos: TodoToolPort,
    private val names: TaskContractToolNames,
) {
    fun contract(input: JsonObject): TaskContract? = input["contract"]?.takeUnless { it == JsonNull }?.let { names.decode(it.toString()) }

    fun execute(
        action: String,
        input: JsonObject,
    ): ToolResult {
        val id = input.requiredString("id")
        val todo = todos.list().firstOrNull { it.id == id } ?: return failure("todos", "Todo not found")
        return when (action) {
            "get-contract" ->
                success(
                    "todos",
                    todo.contract?.let { Json.encodeToString(it.mapToolNames(names::providerName)) } ?: "Legacy task: no explicit contract",
                )
            else -> {
                val offset = (input.string("offset")?.toIntOrNull() ?: 0).coerceAtLeast(0)
                val records = todos.attempts(id, offset, 1).map { it.mapToolNames(names::providerName) }
                success(
                    "todos",
                    buildJsonObject {
                        put("attempts", Json.parseToJsonElement(Json.encodeToString(records)))
                        put("offset", offset)
                        put("pageSize", 1)
                        put("nextOffset", if (records.isEmpty()) JsonNull else kotlinx.serialization.json.JsonPrimitive(offset + 1))
                    }.toString(),
                )
            }
        }
    }
}
