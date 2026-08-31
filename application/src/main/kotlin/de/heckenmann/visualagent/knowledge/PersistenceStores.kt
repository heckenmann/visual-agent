package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.todo.Todo
import java.time.Instant

/**
 * Persisted conversation message exposed to agent and tool consumers.
 *
 * Use cases: UC-0000005, UC-0000041.
 */
data class ConversationRecord(
    val id: String,
    val role: String,
    val content: String,
    val metadata: String?,
    val createdAt: Instant,
    val timelineSequence: Long = 0,
) {
    /** Returns a field value by its persistence-facing name. */
    operator fun get(key: String): Any? =
        when (key) {
            "id" -> id
            "role" -> role
            "content" -> content
            "metadata" -> metadata
            "createdAt" -> createdAt.toString()
            "timelineSequence" -> timelineSequence
            else -> null
        }
}

/**
 * Persisted metadata for one managed workspace file.
 *
 * Use cases: UC-0000023, UC-0000024, UC-0000025, UC-0000026, UC-0000027, UC-0000031.
 */
data class WorkspaceFileRecord(
    val id: String,
    val relativePath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val extractedText: String?,
    val importedAt: Instant,
    val updatedAt: Instant,
)

/**
 * Persisted sub-agent state exposed to orchestration consumers.
 *
 * Use cases: UC-0000015, UC-0000016, UC-0000017, UC-0000018.
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

/** Stores and searches long-term memory records. Use cases: UC-0000002, UC-0000041. */
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

/** Stores application preferences by key. Use cases: UC-0000030, UC-0000035, UC-0000037, UC-0000038. */
interface PreferenceStore : ProviderPreferenceStore {
    /** Returns a stored preference or null when absent. */
    override fun getPreference(key: String): String?

    /** Stores or replaces one preference value. */
    override fun setPreference(
        key: String,
        value: String,
    )
}

/** Stores, pages, searches, and deletes conversation messages. Use cases: UC-0000005, UC-0000032, UC-0000041. */
interface ConversationStore {
    /** Persists one conversation message using the caller-provided immutable identifier. */
    fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String? = null,
    ): String

    /** Returns one persisted message, including its durable timeline ordering key. */
    fun getConversationMessage(id: String): ConversationRecord? = null

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

    /** Deletes a single message by id and returns the affected count. */
    fun deleteConversationMessageById(id: String): Int

    /** Updates the content of a single message by id and returns the affected count. */
    fun updateConversationMessageContent(
        id: String,
        newContent: String,
    ): Int
}

/** Stores metadata for files imported into the managed workspace. Use cases: UC-0000023, UC-0000024, UC-0000026, UC-0000031. */
interface WorkspaceFileStore {
    /** Inserts or replaces one workspace file record. */
    fun saveWorkspaceFile(record: WorkspaceFileRecord)

    /** Returns all workspace files ordered by import time. */
    fun listWorkspaceFiles(): List<WorkspaceFileRecord>

    /** Returns one workspace file by identifier. */
    fun getWorkspaceFile(id: String): WorkspaceFileRecord?

    /** Returns one workspace file by relative path. */
    fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord?

    /** Deletes one workspace file metadata record. */
    fun deleteWorkspaceFile(id: String): Boolean
}

/** Stores and retrieves todo domain objects. Use cases: UC-0000013, UC-0000014. */
interface TodoStore {
    /** Inserts or replaces a todo. */
    fun saveTodo(todo: Todo)

    /**
     * Persists list positions without recording new timeline activity.
     *
     * @param todos Todos whose positions changed through a user-initiated reorder
     */
    fun updateTodoPositions(todos: List<Todo>) {
        todos.forEach(::saveTodo)
    }

    /** Creates a todo only when no normalized description already exists. */
    fun createTodoIfAbsent(todo: Todo): TodoCreation

    /** Returns all persisted todos. */
    fun listTodos(): List<Todo>

    /** Deletes one todo by identifier. */
    fun deleteTodo(todoId: String)

    /** Archives a deleted todo snapshot and removes the active row atomically. */
    fun deleteTodoAndArchive(todo: Todo): Todo {
        deleteTodo(todo.id)
        return todo
    }

    /** Returns deleted todo snapshots that may still be shown in conversation history. */
    fun listDeletedTodos(limit: Int = 100): List<Todo> = emptyList()

    /** Deletes every persisted todo. */
    fun clearTodos()
}

/** Result of an atomic todo creation attempt. */
data class TodoCreation(
    val todo: Todo,
    val created: Boolean,
)

/** Stores and retrieves sub-agent runtime state. Use cases: UC-0000015, UC-0000016, UC-0000017, UC-0000018. */
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

/** Stores and retrieves sub-agent tool configurations. Use cases: UC-0000015, UC-0000019. */
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
 *
 * Use cases: UC-0000001, UC-0000040.
 */
interface PersistenceStores :
    MemoryStore,
    PreferenceStore,
    ConversationStore,
    WorkspaceFileStore,
    TodoStore,
    SubAgentStore,
    SubAgentConfigStore
