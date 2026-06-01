package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Represents TodoDao.
 */
@Component
class TodoDao(
    private val connectionProvider: ConnectionProvider,
) {
    /**
     * Saves or updates one todo.
     *
     * @param todo Todo to persist
     */
    fun saveTodo(todo: Todo) {
        connectionProvider.get().prepareStatement(UPSERT_SQL).use { stmt ->
            stmt.setString(1, todo.id)
            stmt.setString(2, todo.description)
            stmt.setString(3, todo.status.name)
            stmt.setString(4, todo.priority.name)
            stmt.setString(5, todo.assignedAgentId)
            stmt.setString(6, todo.createdAt.toString())
            stmt.setString(7, todo.completedAt?.toString())
            stmt.setString(8, todo.dueDate?.toString())
            stmt.executeUpdate()
        }
    }

    /**
     * Lists all todos ordered by creation time.
     *
     * @return Persisted todos
     */
    fun listTodos(): List<Todo> {
        val todos = mutableListOf<Todo>()
        connectionProvider.get().prepareStatement(SELECT_SQL).use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    todos +=
                        Todo(
                            id = rs.getString("id"),
                            description = rs.getString("description"),
                            status = runCatching { TodoStatus.valueOf(rs.getString("status")) }.getOrDefault(TodoStatus.PENDING),
                            priority = runCatching { TodoPriority.valueOf(rs.getString("priority")) }.getOrDefault(TodoPriority.MEDIUM),
                            assignedAgentId = rs.getString("assigned_agent_id"),
                            createdAt = parseInstant(rs.getString("created_at")) ?: Instant.now(),
                            completedAt = parseInstant(rs.getString("completed_at")),
                            dueDate = parseInstant(rs.getString("due_date")),
                        )
                }
            }
        }
        return todos
    }

    /**
     * Deletes one todo.
     *
     * @param todoId Todo ID to remove
     */
    fun deleteTodo(todoId: String) {
        connectionProvider.get().prepareStatement("DELETE FROM todos WHERE id = ?").use { stmt ->
            stmt.setString(1, todoId)
            stmt.executeUpdate()
        }
    }

    /**
     * Deletes all todos.
     */
    fun clearTodos() {
        connectionProvider.get().prepareStatement("DELETE FROM todos").use { stmt ->
            stmt.executeUpdate()
        }
    }

    private fun parseInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private companion object {
        private val UPSERT_SQL =
            """
            INSERT INTO todos (id, description, status, priority, assigned_agent_id, created_at, completed_at, due_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                description = excluded.description,
                status = excluded.status,
                priority = excluded.priority,
                assigned_agent_id = excluded.assigned_agent_id,
                created_at = excluded.created_at,
                completed_at = excluded.completed_at,
                due_date = excluded.due_date
            """.trimIndent()

        private val SELECT_SQL =
            """
            SELECT id, description, status, priority, assigned_agent_id, created_at, completed_at, due_date
            FROM todos
            ORDER BY created_at ASC
            """.trimIndent()
    }
}
