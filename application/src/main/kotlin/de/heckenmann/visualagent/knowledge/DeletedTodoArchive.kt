package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Persists deleted todo snapshots independently from active todo rows. */
@Service
internal class DeletedTodoArchive(
    private val repository: DeletedTodoRepository,
) {
    /** Archives one snapshot in the caller's transaction. */
    fun archive(todo: Todo) {
        repository.save(todo.toDeletedEntity())
    }

    /** Lists snapshots newest-first for conversation reconstruction. */
    @Transactional(readOnly = true)
    fun list(limit: Int = 100): List<Todo> =
        repository
            .findAllByOrderByUpdatedAtDescIdDesc(PageRequest.of(0, limit.coerceIn(1, 100)))
            .map(DeletedTodoEntity::toDomain)

    /** Removes all snapshots as part of clearing the todo store. */
    fun clear() {
        repository.deleteAllInBatch()
    }
}

private fun Todo.toDeletedEntity(): DeletedTodoEntity =
    DeletedTodoEntity(
        id = id,
        description = description,
        status = status.name,
        position = position,
        assignedAgentId = assignedAgentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        timelineSequence = timelineSequence,
        completedAt = completedAt,
        dueDate = dueDate,
    )

private fun DeletedTodoEntity.toDomain(): Todo =
    Todo(
        id = id,
        description = description,
        status = runCatching { TodoStatus.valueOf(status) }.getOrDefault(TodoStatus.CANCELLED),
        position = position,
        assignedAgentId = assignedAgentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        timelineSequence = timelineSequence,
        completedAt = completedAt,
        dueDate = dueDate,
    )
