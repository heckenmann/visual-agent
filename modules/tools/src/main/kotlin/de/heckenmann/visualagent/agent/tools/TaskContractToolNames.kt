package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.TaskContractCodec
import de.heckenmann.visualagent.task.mapToolNames
import kotlinx.serialization.json.Json

/** Registry-backed boundary between model function names and persisted task tool identities. */
internal class TaskContractToolNames(
    private val definitions: () -> List<ToolDefinition>,
) {
    fun decode(text: String): TaskContract {
        require(text.length <= TaskContractCodec.MAX_CHARACTERS) { "Task contract exceeds the size limit" }
        val ids = definitions().associate { it.name to it.id.value }
        return Json
            .decodeFromString<TaskContract>(text)
            .mapToolNames { name ->
                requireNotNull(ids[name]) { "Unknown tool function: $name" }
            }.also(TaskContractCodec::validate)
    }

    fun providerName(id: String): String = definitions().firstOrNull { it.id.value == id }?.name ?: ToolId(id).toReferenceName()
}
