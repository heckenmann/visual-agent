package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.TaskOperation
import de.heckenmann.visualagent.task.TaskPolicy
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRoutingTest {
    @Test
    fun `language and names do not replace actual capabilities and explicit incompatible assignments wait`() {
        val namedCoder = SubAgent("coder", "Coder", "Writes everything", config = AgentConfig(tools = emptyList()))
        val capable = SubAgent("capable", "Arbeiter", "Generic", config = AgentConfig(tools = listOf("workspace:file")))
        val config = mockk<AgentToolConfigService>()
        every { config.toolsFor(namedCoder) } returns emptySet()
        every { config.toolsFor(capable) } returns setOf(ToolId("workspace:file"))
        val agents = linkedMapOf(namedCoder.id to namedCoder, capable.id to capable)
        val planner = AutonomousTaskPlanner(TodoManager(), agents, mockk(), config, registeredTools = { setOf(ToolId("workspace:file")) })
        listOf("Write a file", "Schreibe eine Datei", "Écris un fichier").forEach { objective ->
            val todo =
                Todo(
                    "task",
                    objective,
                    contract =
                        TaskContract(
                            objective = objective,
                            requiredOperations = listOf(TaskOperation("workspace:file", "writeText")),
                        ),
                )
            assertEquals(capable.id, findNextAssignableTodo(listOf(todo), agents, isCompatible = planner::compatible)?.agent?.id)
            assertNull(
                findNextAssignableTodo(listOf(todo.copy(assignedAgentId = namedCoder.id)), agents, isCompatible = planner::compatible),
            )
        }
        val unavailable = TaskContract(objective = "Browse", requiredOperations = listOf(TaskOperation("browser")))
        assertFalse(planner.compatible(Todo("browse", "Browse", contract = unavailable), capable))
        val absent = AutonomousTaskPlanner(TodoManager(), agents, mockk(), config, registeredTools = { emptySet() })
        assertFalse(
            absent.compatible(
                Todo(
                    "missing",
                    "Write",
                    contract = TaskContract(objective = "Write", requiredOperations = listOf(TaskOperation("workspace:file"))),
                ),
                capable,
            ),
        )
    }

    @Test
    fun `routing treats unsandboxed terminal as incompatible with resource scopes`() {
        val worker = SubAgent("terminal-worker", "Worker", "Commands", config = AgentConfig(tools = listOf("terminal")))
        val config = mockk<AgentToolConfigService>()
        every { config.toolsFor(worker) } returns setOf(ToolId("terminal"))
        val planner =
            AutonomousTaskPlanner(
                TodoManager(),
                mapOf(worker.id to worker),
                mockk(),
                config,
                registeredTools = { setOf(ToolId("terminal")) },
            )
        val operation = TaskOperation("terminal")
        val unrestricted = TaskContract(objective = "Build", requiredOperations = listOf(operation))
        val restricted = unrestricted.copy(policy = TaskPolicy(resources = listOf("allowed")))
        assertTrue(planner.compatible(Todo("unrestricted", "Build", contract = unrestricted), worker))
        assertFalse(planner.compatible(Todo("restricted", "Build", contract = restricted), worker))
    }
}
