package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.todo.Todo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
internal class JpaMemoryStore(
    private val repository: MemoryRepository,
) : MemoryStore {
    @Transactional
    override fun saveMemory(
        content: String,
        tags: List<String>,
    ): String {
        val id = UUID.randomUUID().toString()
        repository.save(MemoryEntity(id = id, content = content, tags = tags.joinToString(","), createdAt = Instant.now()))
        return id
    }

    @Transactional
    override fun saveStructuredKnowledge(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): String = saveMemory(Json.encodeToString(StructuredKnowledge(subject, summary, nextSteps)), listOf(subject))

    @Transactional(readOnly = true)
    override fun searchMemories(
        query: String,
        limit: Int,
    ): List<Memory> =
        repository
            .search(query, PageRequest.of(0, limit.coerceAtLeast(1)))
            .map {
                Memory(
                    id = it.id,
                    content = it.content,
                    tags =
                        it.tags
                            .orEmpty()
                            .split(",")
                            .filter(String::isNotBlank),
                    createdAt = it.createdAt,
                )
            }
}

@Service
internal class JpaPreferenceStore(
    private val repository: PreferenceRepository,
) : PreferenceStore {
    @Transactional(readOnly = true)
    override fun getPreference(key: String): String? = repository.findById(key).orElse(null)?.value

    @Transactional
    override fun setPreference(
        key: String,
        value: String,
    ) {
        val entity = repository.findById(key).orElseGet { PreferenceEntity(key = key) }
        entity.value = value
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }
}

@Service
internal class JpaTodoStore(
    private val repository: TodoRepository,
    private val deletedArchive: DeletedTodoArchive,
    private val timelineSequenceStore: ConversationTimelineSequenceStore,
) : TodoStore {
    @Transactional
    override fun saveTodo(todo: Todo) {
        todo.timelineSequence = timelineSequenceStore.next()
        repository.save(todo.toEntity())
    }

    @Transactional
    override fun claimPendingTodo(
        todoId: String,
        agentId: String,
    ): Todo? {
        val updated =
            repository.claimPendingTodo(
                todoId = todoId,
                agentId = agentId,
                updatedAt = Instant.now(),
                timelineSequence = timelineSequenceStore.next(),
            )
        if (updated != 1) return null
        return repository.findById(todoId).orElseThrow().toDomain()
    }

    @Transactional
    override fun updateTodoPositions(todos: List<Todo>) {
        if (todos.isEmpty()) return
        val positionsById = todos.associate { it.id to it.position }
        repository.findAllById(positionsById.keys).forEach { entity ->
            positionsById[entity.id]?.let { position -> entity.position = position }
        }
    }

    @Transactional
    override fun createTodoIfAbsent(todo: Todo): TodoCreation {
        val normalizedDescription = normalizeDescription(todo.description)
        val existing =
            repository.findAllByOrderByPositionAscIdAsc().firstOrNull {
                normalizeDescription(it.description) ==
                    normalizedDescription
            }
        if (existing != null) return TodoCreation(existing.toDomain(), created = false)
        todo.timelineSequence = timelineSequenceStore.next()
        repository.save(todo.toEntity())
        return TodoCreation(todo, created = true)
    }

    @Transactional(readOnly = true)
    override fun listTodos(): List<Todo> = repository.findAllByOrderByPositionAscIdAsc().map(TodoEntity::toDomain)

    @Transactional
    override fun deleteTodo(todoId: String) = repository.deleteById(todoId)

    @Transactional
    override fun deleteTodoAndArchive(todo: Todo): Todo {
        todo.timelineSequence = timelineSequenceStore.next()
        deletedArchive.archive(todo)
        repository.deleteById(todo.id)
        return todo
    }

    @Transactional(readOnly = true)
    override fun listDeletedTodos(limit: Int): List<Todo> = deletedArchive.list(limit)

    @Transactional
    override fun clearTodos() {
        repository.deleteAllInBatch()
        deletedArchive.clear()
    }

    private fun normalizeDescription(description: String): String = description.trim().replace(Regex("\\s+"), " ").lowercase()
}

@Service
internal class JpaSubAgentStore(
    private val repository: SubAgentRepository,
) : SubAgentStore {
    @Transactional
    override fun saveAgent(agent: PersistedSubAgent): Boolean {
        val inserted = !repository.existsById(agent.id)
        val createdAt = repository.findById(agent.id).orElse(null)?.createdAt ?: agent.createdAt
        repository.save(agent.toEntity(createdAt))
        return inserted
    }

    @Transactional(readOnly = true)
    override fun getAgent(id: String): PersistedSubAgent? = repository.findById(id).orElse(null)?.toRecord()

    @Transactional(readOnly = true)
    override fun listAgents(status: String?): List<PersistedSubAgent> =
        (
            status?.let(repository::findByStatusOrderByCreatedAtDescIdDesc)
                ?: repository.findAllByOrderByCreatedAtDescIdDesc()
        ).map(SubAgentEntity::toRecord)

    @Transactional
    override fun deleteAgent(id: String): Boolean {
        if (!repository.existsById(id)) return false
        repository.deleteById(id)
        return true
    }

    @Transactional
    override fun updateAgentStatus(
        id: String,
        status: String,
        currentTask: String?,
    ): Boolean {
        val entity = repository.findById(id).orElse(null) ?: return false
        entity.status = status
        entity.currentTask = currentTask
        entity.updatedAt = Instant.now()
        repository.save(entity)
        return true
    }
}

@Serializable
private data class StructuredKnowledge(
    val subject: String,
    val summary: String,
    val next_steps: String?,
)

private fun MemoryEntity.toDomain(): Memory =
    Memory(
        id = id,
        content = content,
        tags = tags.orEmpty().split(",").filter(String::isNotBlank),
        createdAt = createdAt,
        embedding = embedding,
    )

private fun PersistedSubAgent.toEntity(originalCreatedAt: Instant): SubAgentEntity =
    SubAgentEntity(
        id = id,
        name = name,
        role = role,
        status = status,
        currentTask = currentTask,
        parentAgentId = parentAgentId,
        config = config,
        createdAt = originalCreatedAt,
        updatedAt = updatedAt,
    )

private fun SubAgentEntity.toRecord(): PersistedSubAgent =
    PersistedSubAgent(id, name, role, status, currentTask, parentAgentId, config, createdAt, updatedAt)
