package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus

/** Maps the todo domain model to the active JPA entity. */
internal fun Todo.toEntity(): TodoEntity =
    TodoEntity(id, description, status.name, position, assignedAgentId, createdAt, updatedAt, completedAt, dueDate)

/** Maps the active JPA entity to the todo domain model. */
internal fun TodoEntity.toDomain(): Todo =
    Todo(
        id = id,
        description = description,
        status = runCatching { TodoStatus.valueOf(status) }.getOrDefault(TodoStatus.PENDING),
        position = position,
        assignedAgentId = assignedAgentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        dueDate = dueDate,
    )
