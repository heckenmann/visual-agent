package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.TaskContractCodec
import de.heckenmann.visualagent.task.TaskOperation
import de.heckenmann.visualagent.task.TaskPolicy
import de.heckenmann.visualagent.task.mapToolNames
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskContractToolNamesTest {
    @Test
    fun `unavailable and oversized historical references remain readable but cannot be submitted`() {
        val names = TaskContractToolNames { emptyList() }
        for (id in listOf("missing:tool", "x".repeat(80))) {
            val contract =
                TaskContract(
                    objective = "Historical task",
                    requiredOperations = listOf(TaskOperation(id)),
                    policy = TaskPolicy(tools = listOf(id), actions = mapOf(id to listOf("read"))),
                )
            TaskContractCodec.validate(contract)
            val exported = contract.mapToolNames(names::providerName)
            assertEquals(ToolId(id).toReferenceName(), exported.requiredOperations.single().tool)
            assertEquals(listOf(ToolId(id).toReferenceName()), exported.policy.tools)
            assertEquals(setOf(ToolId(id).toReferenceName()), exported.policy.actions.keys)
            assertFailsWith<IllegalArgumentException> { names.decode(Json.encodeToString(exported)) }
        }
    }

    @Test
    fun `all registered function names round trip without hard coded aliases`() {
        val definitions =
            listOf("workspace:file", "file:read", "network:dns").map {
                ToolDefinition(ToolId(it), ToolId(it).toFunctionName(), "Test", "{}")
            }
        val names = TaskContractToolNames { definitions }
        for (definition in definitions) {
            val modelContract =
                TaskContract(
                    objective = "Keep workspace_file prose intact",
                    requiredOperations = listOf(TaskOperation(definition.name, "readText")),
                    policy = TaskPolicy(tools = listOf(definition.name), actions = mapOf(definition.name to listOf("readText"))),
                )
            val stored = names.decode(Json.encodeToString(modelContract))
            assertEquals(definition.id.value, stored.requiredOperations.single().tool)
            assertEquals(listOf(definition.id.value), stored.policy.tools)
            assertEquals(setOf(definition.id.value), stored.policy.actions.keys)
            assertEquals(stored, TaskContractCodec.decode(TaskContractCodec.encode(stored)))
            assertEquals(modelContract, stored.mapToolNames(names::providerName))
        }
        assertFailsWith<IllegalArgumentException> {
            names.decode(
                Json.encodeToString(TaskContract(objective = "Unknown", requiredOperations = listOf(TaskOperation("missing_tool")))),
            )
        }
    }
}
