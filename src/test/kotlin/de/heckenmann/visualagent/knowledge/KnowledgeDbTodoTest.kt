package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnowledgeDbTodoTest {
    @Test
    fun `todo crud is persisted in sqlite`() {
        val tempDb = createTempDirectory("visual-agent-db-todo-test").resolve("todos.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)

        val todo =
            Todo(
                id = "todo-1",
                description = "Persisted todo",
                status = TodoStatus.PENDING,
                position = 1,
                createdAt = Instant.now(),
            )
        db.saveTodo(todo)
        assertEquals(1, db.listTodos().size)

        todo.status = TodoStatus.IN_PROGRESS
        todo.assignedAgentId = "agent-1"
        db.saveTodo(todo)
        val updated = db.listTodos().first()
        assertEquals(TodoStatus.IN_PROGRESS, updated.status)
        assertEquals("agent-1", updated.assignedAgentId)

        db.deleteTodo("todo-1")
        assertTrue(db.listTodos().isEmpty())
        db.close()
    }
}
