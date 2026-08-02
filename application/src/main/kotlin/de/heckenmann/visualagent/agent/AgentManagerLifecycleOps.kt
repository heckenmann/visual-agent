package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.knowledge.PersistedSubAgent
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChangeType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * Handles agent lifecycle and persistence operations for [AgentManager].
 */
internal class AgentManagerLifecycleOps(
    private val owner: AgentManager,
) {
    private val logger = KotlinLogging.logger {}

    fun loadAgentsFromDb() {
        val agents = owner.subAgentStore.listAgents()
        logger.info { "Found ${agents.size} agents in DB" }
        if (agents.size < 3) {
            createDefaultAgents()
            logger.info { "Created default agents; subAgents size=${owner.subAgentOpsProvider.allSubAgents.size}" }
            return
        }

        val sortedAgents = agents.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
        sortedAgents.forEach { agentMap ->
            try {
                val agent = mapAgentRecord(agentMap, resetStatusToIdle = true)
                logger.debug { "Loading agent id=${agent.id} status=${agent.status}" }
                owner.subAgentOpsProvider.putSubAgent(agent)
            } catch (e: Exception) {
                logger.warn(e) { "Error loading agent ${agentMap.id}" }
            }
        }
        logger.debug {
            "Loaded subAgents keys: ${owner.subAgentOpsProvider.allSubAgents.keys} " +
                "and statuses=${owner.subAgentOpsProvider.allSubAgents.mapValues { it.value.status }}"
        }
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
                } catch (e: Exception) {
                    logger.warn(e) { "Could not parse agent config for ${agentRecord.id}; using defaults" }
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
            owner.subAgentOpsProvider.putSubAgent(agent)
        }
        logger.info { "Created ${defaults.size} default agents" }
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

    fun getSubAgents(): List<SubAgent> =
        owner.subAgentOpsProvider.allSubAgents.values
            .toList()

    fun getSubAgent(id: String): SubAgent? = owner.subAgentOpsProvider.getSubAgent(id)

    fun getTodosFromDb(): List<Todo> = owner.todoStore.listTodos()

    fun getTodoSummaryFromDb(): TodoSummary {
        val todos = owner.todoStore.listTodos()
        return TodoSummary(
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
        owner.subAgentOpsProvider.putSubAgent(agent)
        owner.agentStatusCallbackAdapter.notify(agent.id, "CREATED")
        logger.info { "Created agent: $id ($name)" }
        return agent
    }

    fun updateAgent(
        id: String,
        name: String? = null,
        role: String? = null,
        config: AgentConfig? = null,
    ): Boolean {
        val agent = owner.subAgentOpsProvider.getSubAgent(id) ?: return false
        if (name != null) agent.name = name
        if (role != null) agent.role = role
        if (config != null) agent.config = config
        agent.updatedAt = System.currentTimeMillis()
        saveAgentToDb(agent)
        logger.debug { "Updated agent: $id" }
        return true
    }

    fun deleteAgent(id: String): Boolean {
        val removed = owner.subAgentOpsProvider.removeSubAgent(id)
        if (removed != null) {
            if (removed.status == AgentStatus.BUSY) {
                owner.autonomousCoordinator.cancelAgentTodo(id)
            }
            owner.subAgentStore.deleteAgent(id)
            persistTodoChangeMessage("Deleted sub-agent $id (${removed.name})")
            logger.info { "Deleted agent: $id" }
            return true
        }
        return false
    }

    fun persistTodoChangeMessage(content: String) {
        owner.conversationOps.persist(Message(role = "system", content = content))
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
                persistTodoChangeMessage(formatTodoChangeMessage(change.type, todo))
            }
            de.heckenmann.visualagent.todo.TodoChangeType.REMOVED -> {
                val todoId = change.todoId ?: return
                persistTodoChangeMessage("Removed todo $todoId")
            }
            de.heckenmann.visualagent.todo.TodoChangeType.REORDERED -> {
                val moved = change.todo?.let { " (${it.description.take(60)}, id=${it.id})" } ?: ""
                persistTodoChangeMessage("Reordered todo list$moved")
            }
            de.heckenmann.visualagent.todo.TodoChangeType.CLEARED -> {
                persistTodoChangeMessage("Cleared all todos")
            }
        }
    }

    private fun formatTodoChangeMessage(
        type: TodoChangeType,
        todo: Todo,
    ): String {
        val base =
            when (type) {
                TodoChangeType.ADDED -> "Created todo"
                TodoChangeType.UPDATED -> "Updated todo"
                else -> "Todo changed"
            }
        val assigned = todo.assignedAgentId?.let { " assigned to $it" }.orEmpty()
        return "$base ${todo.id} (${todo.description.take(80)})$assigned [${todo.status}]"
    }
}
