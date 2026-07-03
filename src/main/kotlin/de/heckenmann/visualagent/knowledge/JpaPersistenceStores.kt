package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
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
    ): List<Memory> = repository.search(query, PageRequest.of(0, limit.coerceAtLeast(1))).map(MemoryEntity::toDomain)
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
internal class JpaConversationStore(
    private val repository: ConversationRepository,
) : ConversationStore {
    @Transactional
    override fun saveConversationMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
    ): String {
        val id = UUID.randomUUID().toString()
        repository.save(
            ConversationEntity(
                id = id,
                sessionId = sessionId,
                role = role,
                content = content,
                metadata = metadata,
                createdAt = Instant.now(),
            ),
        )
        return id
    }

    @Transactional(readOnly = true)
    override fun getConversationMessages(
        sessionId: String,
        limit: Int,
    ): List<ConversationRecord> =
        repository
            .findBySessionIdOrderByCreatedAtDescIdDesc(sessionId, PageRequest.of(0, limit.coerceAtLeast(1)))
            .asReversed()
            .map(ConversationEntity::toRecord)

    @Transactional(readOnly = true)
    override fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationRecord> =
        repository
            .findPage(sessionId, limit.coerceAtLeast(1), offset.coerceAtLeast(0))
            .asReversed()
            .map(ConversationEntity::toRecord)

    override fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationRecord> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)
        return runCatching {
            if (isSafeFtsQuery(normalized)) {
                repository.searchFts(sessionId, normalized, safeLimit)
            } else {
                repository.searchLike(sessionId, normalized, safeLimit)
            }
        }.getOrElse { repository.searchLike(sessionId, normalized, safeLimit) }
            .map(ConversationEntity::toRecord)
    }

    @Transactional
    override fun deleteConversationMessages(sessionId: String): Int = repository.deleteBySessionId(sessionId)

    @Transactional
    override fun deleteConversationMessageById(id: String): Int {
        repository.deleteById(id)
        return 1
    }

    @Transactional
    override fun updateConversationMessageContent(
        id: String,
        newContent: String,
    ): Int {
        val entity = repository.findByIdOrNull(id) ?: return 0
        entity.content = newContent
        repository.save(entity)
        return 1
    }
}

@Service
internal class JpaTodoStore(
    private val repository: TodoRepository,
) : TodoStore {
    @Transactional
    override fun saveTodo(todo: Todo) {
        repository.save(todo.toEntity())
    }

    @Transactional(readOnly = true)
    override fun listTodos(): List<Todo> = repository.findAllByOrderByCreatedAtAscIdAsc().map(TodoEntity::toDomain)

    @Transactional
    override fun deleteTodo(todoId: String) = repository.deleteById(todoId)

    @Transactional
    override fun clearTodos() = repository.deleteAllInBatch()
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

@Service
internal class JpaSubAgentConfigStore(
    private val repository: SubAgentConfigRepository,
) : SubAgentConfigStore {
    @Transactional
    override fun saveSubAgentConfig(config: SubAgentToolConfig) {
        val createdAt = repository.findById(config.id).orElse(null)?.createdAt ?: Instant.now()
        repository.save(
            SubAgentConfigEntity(
                id = config.id,
                name = config.name,
                description = config.description,
                model = config.model,
                systemPrompt = config.systemPrompt,
                tools = Json.encodeToString(config.tools),
                maxTurns = config.maxTurns,
                enabled = config.enabled,
                createdAt = createdAt,
            ),
        )
    }

    @Transactional(readOnly = true)
    override fun getSubAgentConfig(id: String): SubAgentToolConfig? = repository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun listSubAgentConfigs(): List<SubAgentToolConfig> = repository.findAllByOrderByIdAsc().map(SubAgentConfigEntity::toDomain)
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

private fun ConversationEntity.toRecord(): ConversationRecord = ConversationRecord(id, role, content, metadata, createdAt)

private fun Todo.toEntity(): TodoEntity =
    TodoEntity(id, description, status.name, priority.name, assignedAgentId, createdAt, completedAt, dueDate)

private fun TodoEntity.toDomain(): Todo =
    Todo(
        id = id,
        description = description,
        status = runCatching { TodoStatus.valueOf(status) }.getOrDefault(TodoStatus.PENDING),
        priority = runCatching { TodoPriority.valueOf(priority) }.getOrDefault(TodoPriority.MEDIUM),
        assignedAgentId = assignedAgentId,
        createdAt = createdAt,
        completedAt = completedAt,
        dueDate = dueDate,
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

private fun SubAgentConfigEntity.toDomain(): SubAgentToolConfig =
    SubAgentToolConfig(
        id = id,
        name = name,
        description = description,
        model = model,
        systemPrompt = systemPrompt,
        tools = runCatching { Json.decodeFromString<List<String>>(tools) }.getOrElse { emptyList() },
        maxTurns = maxTurns,
        enabled = enabled,
    )

private fun isSafeFtsQuery(query: String): Boolean =
    query.isNotBlank() &&
        query.length <= 128 &&
        query.all { it.isLetterOrDigit() || it.isWhitespace() || it == '_' }
