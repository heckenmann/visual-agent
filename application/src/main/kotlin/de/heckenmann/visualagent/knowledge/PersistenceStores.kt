package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.todo.Todo
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

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

/** Persisted access grant for a filesystem root owned by the server or a connected client. */
data class DirectoryGrantRecord(
    val id: String,
    val displayName: String,
    val canonicalRoot: String?,
    val origin: String,
    val mode: String,
    val ownerClientId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Stores directory grants without caching filesystem authorization state. */
interface DirectoryGrantStore {
    /** Persists a new or updated grant. */
    fun saveDirectoryGrant(record: DirectoryGrantRecord)

    /** Lists grants in stable creation order. */
    fun listDirectoryGrants(): List<DirectoryGrantRecord>

    /** Looks up a grant by opaque identifier. */
    fun getDirectoryGrant(id: String): DirectoryGrantRecord?

    /** Looks up a server grant by its canonical root for alias detection. */
    fun getDirectoryGrantByCanonicalRoot(canonicalRoot: String): DirectoryGrantRecord?

    /** Revokes a grant and returns whether it existed. */
    fun deleteDirectoryGrant(id: String): Boolean

    /** Reactive counterpart of [saveDirectoryGrant]. */
    fun saveDirectoryGrantReactive(record: DirectoryGrantRecord): Mono<Void> = Mono.fromRunnable { saveDirectoryGrant(record) }

    /** Reactive counterpart of [listDirectoryGrants]. */
    fun listDirectoryGrantsReactive(): Flux<DirectoryGrantRecord> = Flux.defer { Flux.fromIterable(listDirectoryGrants()) }

    /** Reactive counterpart of [getDirectoryGrant]. */
    fun getDirectoryGrantReactive(id: String): Mono<DirectoryGrantRecord> = Mono.fromCallable { getDirectoryGrant(id) }

    /** Reactive counterpart of [getDirectoryGrantByCanonicalRoot]. */
    fun getDirectoryGrantByCanonicalRootReactive(canonicalRoot: String): Mono<DirectoryGrantRecord> =
        Mono.fromCallable { getDirectoryGrantByCanonicalRoot(canonicalRoot) }

    /** Reactive counterpart of [deleteDirectoryGrant]. */
    fun deleteDirectoryGrantReactive(id: String): Mono<Boolean> = Mono.fromCallable { deleteDirectoryGrant(id) }
}

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

    /** Reactive counterpart of [saveMemory]. */
    fun saveMemoryReactive(
        content: String,
        tags: List<String> = emptyList(),
    ): Mono<String> = Mono.fromCallable { saveMemory(content, tags) }

    /** Reactive counterpart of [saveStructuredKnowledge]. */
    fun saveStructuredKnowledgeReactive(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): Mono<String> = Mono.fromCallable { saveStructuredKnowledge(subject, summary, nextSteps) }

    /** Reactive counterpart of [searchMemories]. */
    fun searchMemoriesReactive(
        query: String,
        limit: Int = 10,
    ): Flux<Memory> = Flux.defer { Flux.fromIterable(searchMemories(query, limit)) }
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

    /** Reactive counterpart of [getPreference]. */
    override fun getPreferenceReactive(key: String): Mono<String> = Mono.fromCallable { getPreference(key) }

    /** Reactive counterpart of [setPreference]. */
    override fun setPreferenceReactive(
        key: String,
        value: String,
    ): Mono<Void> = Mono.fromRunnable { setPreference(key, value) }
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

    /** Reactive counterpart of [saveWorkspaceFile]. */
    fun saveWorkspaceFileReactive(record: WorkspaceFileRecord): Mono<Void> = Mono.fromRunnable { saveWorkspaceFile(record) }

    /** Reactive counterpart of [listWorkspaceFiles]. */
    fun listWorkspaceFilesReactive(): Flux<WorkspaceFileRecord> = Flux.defer { Flux.fromIterable(listWorkspaceFiles()) }

    /** Reactive counterpart of [getWorkspaceFile]. */
    fun getWorkspaceFileReactive(id: String): Mono<WorkspaceFileRecord> = Mono.fromCallable { getWorkspaceFile(id) }

    /** Reactive counterpart of [getWorkspaceFileByPath]. */
    fun getWorkspaceFileByPathReactive(relativePath: String): Mono<WorkspaceFileRecord> =
        Mono.fromCallable { getWorkspaceFileByPath(relativePath) }

    /** Reactive counterpart of [deleteWorkspaceFile]. */
    fun deleteWorkspaceFileReactive(id: String): Mono<Boolean> = Mono.fromCallable { deleteWorkspaceFile(id) }
}

/** Stores and retrieves todo domain objects. Use cases: UC-0000013, UC-0000014. */
interface TodoStore {
    /** Inserts or replaces a todo. */
    fun saveTodo(todo: Todo)

    /**
     * Atomically assigns and claims a pending todo for execution.
     *
     * @return The claimed todo, or `null` when it is no longer pending
     */
    fun claimPendingTodo(
        todoId: String,
        agentId: String,
    ): Todo?

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

    /** Reactive counterpart of [saveTodo]. */
    fun saveTodoReactive(todo: Todo): Mono<Void> = Mono.fromRunnable { saveTodo(todo) }

    /** Reactive counterpart of [claimPendingTodo]. */
    fun claimPendingTodoReactive(
        todoId: String,
        agentId: String,
    ): Mono<Todo> = Mono.fromCallable { claimPendingTodo(todoId, agentId) }

    /** Reactive counterpart of [updateTodoPositions]. */
    fun updateTodoPositionsReactive(todos: List<Todo>): Mono<Void> = Mono.fromRunnable { updateTodoPositions(todos) }

    /** Reactive counterpart of [createTodoIfAbsent]. */
    fun createTodoIfAbsentReactive(todo: Todo): Mono<TodoCreation> = Mono.fromCallable { createTodoIfAbsent(todo) }

    /** Reactive counterpart of [listTodos]. */
    fun listTodosReactive(): Flux<Todo> = Flux.defer { Flux.fromIterable(listTodos()) }

    /** Reactive counterpart of [deleteTodo]. */
    fun deleteTodoReactive(todoId: String): Mono<Void> = Mono.fromRunnable { deleteTodo(todoId) }

    /** Reactive counterpart of [deleteTodoAndArchive]. */
    fun deleteTodoAndArchiveReactive(todo: Todo): Mono<Todo> = Mono.fromCallable { deleteTodoAndArchive(todo) }

    /** Reactive counterpart of [listDeletedTodos]. */
    fun listDeletedTodosReactive(limit: Int = 100): Flux<Todo> = Flux.defer { Flux.fromIterable(listDeletedTodos(limit)) }

    /** Reactive counterpart of [clearTodos]. */
    fun clearTodosReactive(): Mono<Void> = Mono.fromRunnable { clearTodos() }
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

    /** Reactive counterpart of [saveAgent]. */
    fun saveAgentReactive(agent: PersistedSubAgent): Mono<Boolean> = Mono.fromCallable { saveAgent(agent) }

    /** Reactive counterpart of [getAgent]. */
    fun getAgentReactive(id: String): Mono<PersistedSubAgent> = Mono.fromCallable { getAgent(id) }

    /** Reactive counterpart of [listAgents]. */
    fun listAgentsReactive(status: String? = null): Flux<PersistedSubAgent> = Flux.defer { Flux.fromIterable(listAgents(status)) }

    /** Reactive counterpart of [deleteAgent]. */
    fun deleteAgentReactive(id: String): Mono<Boolean> = Mono.fromCallable { deleteAgent(id) }

    /** Reactive counterpart of [updateAgentStatus]. */
    fun updateAgentStatusReactive(
        id: String,
        status: String,
        currentTask: String? = null,
    ): Mono<Boolean> = Mono.fromCallable { updateAgentStatus(id, status, currentTask) }
}

/** Stores and retrieves sub-agent tool configurations. Use cases: UC-0000015, UC-0000019. */
interface SubAgentConfigStore {
    /** Inserts or replaces a sub-agent tool configuration. */
    fun saveSubAgentConfig(config: SubAgentToolConfig)

    /** Returns one sub-agent tool configuration. */
    fun getSubAgentConfig(id: String): SubAgentToolConfig?

    /** Returns all sub-agent tool configurations. */
    fun listSubAgentConfigs(): List<SubAgentToolConfig>

    /** Reactive counterpart of [saveSubAgentConfig]. */
    fun saveSubAgentConfigReactive(config: SubAgentToolConfig): Mono<Void> = Mono.fromRunnable { saveSubAgentConfig(config) }

    /** Reactive counterpart of [getSubAgentConfig]. */
    fun getSubAgentConfigReactive(id: String): Mono<SubAgentToolConfig> = Mono.fromCallable { getSubAgentConfig(id) }

    /** Reactive counterpart of [listSubAgentConfigs]. */
    fun listSubAgentConfigsReactive(): Flux<SubAgentToolConfig> = Flux.defer { Flux.fromIterable(listSubAgentConfigs()) }
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
