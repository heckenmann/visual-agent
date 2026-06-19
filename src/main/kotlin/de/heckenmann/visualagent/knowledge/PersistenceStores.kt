package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.todo.Todo
import java.time.Instant

/**
 * Persisted conversation message exposed to agent and tool consumers.
 */
data class ConversationRecord(
    val id: String,
    val role: String,
    val content: String,
    val metadata: String?,
    val createdAt: Instant,
) {
    /** Returns a field value by its persistence-facing name. */
    operator fun get(key: String): Any? =
        when (key) {
            "id" -> id
            "role" -> role
            "content" -> content
            "metadata" -> metadata
            "createdAt" -> createdAt.toString()
            else -> null
        }
}

/**
 * Persisted sub-agent state exposed to orchestration consumers.
 */
data class PersistedSubAgent(
    val id: String,
    val name: String,
    val role: String,
    val status: String,
    val currentTask: String?,
    val parentAgentId: String?,
    val config: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Returns a field value by its persistence-facing name. */
    operator fun get(key: String): Any? =
        when (key) {
            "id" -> id
            "name" -> name
            "role" -> role
            "status" -> status
            "currentTask" -> currentTask
            "parentAgentId" -> parentAgentId
            "config" -> config
            "createdAt" -> createdAt.toString()
            "updatedAt" -> updatedAt.toString()
            else -> null
        }
}

/** Stores and searches long-term memory records. */
interface MemoryStore {
    /** Persists free-form memory and returns its identifier. */
    fun saveMemory(
        content: String,
        tags: List<String> = emptyList(),
    ): String

    /** Persists structured knowledge and returns its identifier. */
    fun saveStructuredKnowledge(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): String

    /** Searches memory records ordered by relevance. */
    fun searchMemories(
        query: String,
        limit: Int = 10,
    ): List<Memory>
}

/** Stores application preferences by key. */
interface PreferenceStore {
    /** Returns a stored preference or null when absent. */
    fun getPreference(key: String): String?

    /** Stores or replaces one preference value. */
    fun setPreference(
        key: String,
        value: String,
    )
}

/** Stores, pages, searches, and deletes conversation messages. */
interface ConversationStore {
    /** Persists one conversation message and returns its identifier. */
    fun saveConversationMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
    ): String

    /** Returns the latest messages for a session. */
    fun getConversationMessages(
        sessionId: String,
        limit: Int = 500,
    ): List<ConversationRecord>

    /** Returns one deterministic page of session messages. */
    fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationRecord>

    /** Searches session messages by text query. */
    fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int = 20,
    ): List<ConversationRecord>

    /** Deletes all messages for a session and returns the affected count. */
    fun deleteConversationMessages(sessionId: String): Int
}

/** Stores and retrieves todo domain objects. */
interface TodoStore {
    /** Inserts or replaces a todo. */
    fun saveTodo(todo: Todo)

    /** Returns all persisted todos. */
    fun listTodos(): List<Todo>

    /** Deletes one todo by identifier. */
    fun deleteTodo(todoId: String)

    /** Deletes every persisted todo. */
    fun clearTodos()
}

/** Stores and retrieves sub-agent runtime state. */
interface SubAgentStore {
    /** Inserts or replaces sub-agent state. */
    fun saveAgent(agent: PersistedSubAgent): Boolean

    /** Returns one persisted sub-agent by identifier. */
    fun getAgent(id: String): PersistedSubAgent?

    /** Lists sub-agents, optionally restricted to one status. */
    fun listAgents(status: String? = null): List<PersistedSubAgent>

    /** Deletes one sub-agent by identifier. */
    fun deleteAgent(id: String): Boolean

    /** Updates runtime status and current task for a sub-agent. */
    fun updateAgentStatus(
        id: String,
        status: String,
        currentTask: String? = null,
    ): Boolean
}

/** Stores and retrieves sub-agent tool configurations. */
interface SubAgentConfigStore {
    /** Inserts or replaces a sub-agent tool configuration. */
    fun saveSubAgentConfig(config: SubAgentToolConfig)

    /** Returns one sub-agent tool configuration. */
    fun getSubAgentConfig(id: String): SubAgentToolConfig?

    /** Returns all sub-agent tool configurations. */
    fun listSubAgentConfigs(): List<SubAgentToolConfig>
}

/**
 * Groups all persistence capabilities for bootstrap and isolated integration-test wiring.
 */
interface PersistenceStores :
    MemoryStore,
    PreferenceStore,
    ConversationStore,
    TodoStore,
    SubAgentStore,
    SubAgentConfigStore
