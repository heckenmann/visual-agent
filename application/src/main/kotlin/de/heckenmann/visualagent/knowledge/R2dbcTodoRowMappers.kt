package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.task.TaskAttemptResult
import de.heckenmann.visualagent.task.TaskContractCodec
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import java.time.Instant

/** Maps a database row into a persisted todo. */
internal fun Row.toTodo(): Todo =
    Todo(
        acceptedResult = R2dbcPersistenceSupport.text(this, "accepted_result")?.let { Json.decodeFromString<TaskAttemptResult>(it) },
        contract = R2dbcPersistenceSupport.text(this, "task_contract")?.let(TaskContractCodec::decode),
        id = R2dbcPersistenceSupport.requiredText(this, "id"),
        description = R2dbcPersistenceSupport.requiredText(this, "description"),
        status = runCatching { TodoStatus.valueOf(R2dbcPersistenceSupport.requiredText(this, "status")) }.getOrDefault(TodoStatus.PENDING),
        position = R2dbcPersistenceSupport.integer(this, "position") ?: 0,
        assignedAgentId = R2dbcPersistenceSupport.text(this, "assigned_agent_id"),
        createdAt = R2dbcPersistenceSupport.instant(this, "created_at") ?: Instant.EPOCH,
        updatedAt = R2dbcPersistenceSupport.instant(this, "updated_at") ?: Instant.EPOCH,
        timelineSequence = R2dbcPersistenceSupport.long(this, "timeline_sequence") ?: 0L,
        completedAt = R2dbcPersistenceSupport.instant(this, "completed_at"),
        dueDate = R2dbcPersistenceSupport.instant(this, "due_date"),
        decompositionDepth = R2dbcPersistenceSupport.integer(this, "decomposition_depth") ?: 0,
        terminalDetail = R2dbcPersistenceSupport.text(this, "terminal_detail"),
    )
