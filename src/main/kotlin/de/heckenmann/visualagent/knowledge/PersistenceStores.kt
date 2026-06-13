package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.SubAgentToolConfig
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
    fun saveMemory(
        content: String,
        tags: List<String> = emptyList(),
    ): String

    fun saveStructuredKnowledge(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): String

    fun searchMemories(
        query: String,
        limit: Int = 10,
    ): List<Memory>
}

/** Stores application preferences by key. */
interface PreferenceStore {
    fun getPreference(key: String): String?

    fun setPreference(
        key: String,
        value: String,
    )
}

/** Stores, pages, searches, and deletes conversation messages. */
interface ConversationStore {
    fun saveConversationMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
    ): String

    fun getConversationMessages(
        sessionId: String,
        limit: Int = 500,
    ): List<ConversationRecord>

    fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationRecord>

    fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int = 20,
    ): List<ConversationRecord>

    fun deleteConversationMessages(sessionId: String): Int
}

/** Stores and retrieves todo domain objects. */
interface TodoStore {
    fun saveTodo(todo: Todo)

    fun listTodos(): List<Todo>

    fun deleteTodo(todoId: String)

    fun clearTodos()
}

/** Stores and retrieves sub-agent runtime state. */
interface SubAgentStore {
    fun saveAgent(agent: PersistedSubAgent): Boolean

    fun getAgent(id: String): PersistedSubAgent?

    fun listAgents(status: String? = null): List<PersistedSubAgent>

    fun deleteAgent(id: String): Boolean

    fun updateAgentStatus(
        id: String,
        status: String,
        currentTask: String? = null,
    ): Boolean
}

/** Stores and retrieves sub-agent tool configurations. */
interface SubAgentConfigStore {
    fun saveSubAgentConfig(config: SubAgentToolConfig)

    fun getSubAgentConfig(id: String): SubAgentToolConfig?

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
