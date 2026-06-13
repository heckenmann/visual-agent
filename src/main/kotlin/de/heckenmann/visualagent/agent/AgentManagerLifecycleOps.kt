package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.PersistedSubAgent
import de.heckenmann.visualagent.todo.Todo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/**
 * Handles agent lifecycle and persistence operations for [AgentManager].
 */
internal class AgentManagerLifecycleOps(
    private val owner: AgentManager,
) {
    fun loadAgentsFromDb() {
        val agents = owner.subAgentStore.listAgents()
        println("[AgentManager] Found ${agents.size} agents in DB")
        if (agents.size < 3) {
            createDefaultAgents()
            println("[AgentManager] Created default agents; subAgents size=${owner.subAgents.size}")
            return
        }

        val sortedAgents = agents.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
        sortedAgents.forEach { agentMap ->
            try {
                val agent = mapAgentRecord(agentMap, resetStatusToIdle = true)
                println("[AgentManager] Loading agent id=${agent.id} status=${agent.status}")
                owner.subAgents[agent.id] = agent
            } catch (e: Exception) {
                println("[AgentManager] Error loading agent: ${e.message}")
            }
        }
        println(
            "[AgentManager] Loaded subAgents keys: ${owner.subAgents.keys} and statuses=${owner.subAgents.mapValues { it.value.status }}",
        )
    }

    fun mapAgentRecord(
        agentRecord: PersistedSubAgent,
        resetStatusToIdle: Boolean,
    ): SubAgent {
        val persistedStatus =
            runCatching {
                AgentStatus.valueOf(agentRecord.status)
            }.getOrDefault(AgentStatus.IDLE)
        return SubAgent(
            id = agentRecord.id,
            name = agentRecord.name,
            role = agentRecord.role,
            status = if (resetStatusToIdle) AgentStatus.IDLE else persistedStatus,
            currentTask = agentRecord.currentTask?.ifBlank { null },
            parentAgentId = agentRecord.parentAgentId?.ifBlank { null },
            config =
                try {
                    Json.decodeFromString(agentRecord.config)
                } catch (_: Exception) {
                    AgentConfig()
                },
            createdAt = agentRecord.createdAt.toEpochMilli(),
            updatedAt = agentRecord.updatedAt.toEpochMilli(),
        )
    }

    fun createDefaultAgents() {
        val defaults =
            listOf(
                SubAgent(
                    "1",
                    "Researcher",
                    "Web research and information gathering",
                    AgentStatus.IDLE,
                    config = AgentConfig.fromTemplate("researcher"),
                ),
                SubAgent("2", "Coder", "Code implementation and review", AgentStatus.IDLE, config = AgentConfig.fromTemplate("coder")),
                SubAgent("3", "Documenter", "Documentation writing", AgentStatus.IDLE, config = AgentConfig.fromTemplate("documenter")),
            )
        defaults.forEach { agent ->
            saveAgentToDb(agent)
            owner.subAgents[agent.id] = agent
        }
        println("[AgentManager] Created ${defaults.size} default agents")
    }

    fun saveAgentToDb(agent: SubAgent) {
        val configJson = Json.encodeToString(agent.config)
        owner.subAgentStore.saveAgent(
            PersistedSubAgent(
                id = agent.id,
                name = agent.name,
                role = agent.role,
                status = agent.status.name,
                currentTask = agent.currentTask,
                parentAgentId = agent.parentAgentId,
                config = configJson,
                createdAt = Instant.ofEpochMilli(agent.createdAt),
                updatedAt = Instant.now(),
            ),
        )
    }

    fun getSubAgents(): List<SubAgent> = owner.subAgents.values.toList()

    fun getSubAgent(id: String): SubAgent? = owner.subAgents[id]

    fun getTodosFromDb(): List<Todo> = owner.todoStore.listTodos()

    fun getTodoSummaryFromDb(): AgentManager.TodoSummary {
        val todos = owner.todoStore.listTodos()
        return AgentManager.TodoSummary(
            total = todos.size,
            open = todos.count { it.status == de.heckenmann.visualagent.todo.TodoStatus.PENDING },
            inProgress = todos.count { it.status == de.heckenmann.visualagent.todo.TodoStatus.IN_PROGRESS },
            completed = todos.count { it.status == de.heckenmann.visualagent.todo.TodoStatus.COMPLETED },
            cancelled = todos.count { it.status == de.heckenmann.visualagent.todo.TodoStatus.CANCELLED },
        )
    }

    fun createAgent(
        name: String,
        role: String,
        templateName: String = "researcher",
    ): SubAgent {
        val id = UUID.randomUUID().toString().take(8)
        val agent = SubAgent.fromTemplate(id, name, role, templateName)
        saveAgentToDb(agent)
        owner.subAgents[agent.id] = agent
        AgentManager.notifyAgent(agent.id, "CREATED")
        println("[AgentManager] Created agent: $id ($name)")
        return agent
    }

    fun updateAgent(
        id: String,
        name: String? = null,
        role: String? = null,
        config: AgentConfig? = null,
    ): Boolean {
        val agent = owner.subAgents[id] ?: return false
        if (name != null) agent.name = name
        if (role != null) agent.role = role
        if (config != null) agent.config = config
        agent.updatedAt = System.currentTimeMillis()
        saveAgentToDb(agent)
        println("[AgentManager] Updated agent: $id")
        return true
    }

    fun deleteAgent(id: String): Boolean {
        val removed = owner.subAgents.remove(id)
        if (removed != null) {
            owner.subAgentStore.deleteAgent(id)
            println("[AgentManager] Deleted agent: $id")
            return true
        }
        return false
    }

    fun getSubAgentsFromDb(): List<SubAgent> = listSubAgentsFromDb()

    fun listSubAgentsFromDb(): List<SubAgent> =
        owner.subAgentStore
            .listAgents()
            .sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { row ->
                runCatching { mapAgentRecord(row, resetStatusToIdle = false) }.getOrNull()
            }

    fun persistTodoChange(change: de.heckenmann.visualagent.todo.TodoChange) {
        when (change.type) {
            de.heckenmann.visualagent.todo.TodoChangeType.ADDED,
            de.heckenmann.visualagent.todo.TodoChangeType.UPDATED,
            -> {
                val todo = change.todo ?: return
                owner.todoStore.saveTodo(todo)
            }
            de.heckenmann.visualagent.todo.TodoChangeType.REMOVED -> {
                val todoId = change.todoId ?: return
                owner.todoStore.deleteTodo(todoId)
            }
            de.heckenmann.visualagent.todo.TodoChangeType.CLEARED -> owner.todoStore.clearTodos()
        }
    }
}
