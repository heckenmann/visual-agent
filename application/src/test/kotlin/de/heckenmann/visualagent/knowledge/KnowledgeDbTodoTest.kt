package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class KnowledgeDbTodoTest {
    @Test
    fun `todo schema does not retain the removed priority column`() {
        val tempDb =
            createTempDirectory("visual-agent-db-todo-schema-test")
                .resolve("todos.db")
                .toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        db.close()

        DriverManager.getConnection("jdbc:sqlite:$tempDb").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA table_info(todos)").use { columns ->
                    val names =
                        buildList {
                            while (columns.next()) add(columns.getString("name"))
                        }
                    assertFalse(names.contains("priority"))
                    assertTrue(names.contains("updated_at"))
                }
            }
        }
    }

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
        todo.updatedAt = Instant.ofEpochMilli(1234)
        db.saveTodo(todo)
        val updated = db.listTodos().first()
        assertEquals(TodoStatus.IN_PROGRESS, updated.status)
        assertEquals("agent-1", updated.assignedAgentId)
        assertEquals(Instant.ofEpochMilli(1234), updated.updatedAt)

        db.deleteTodo("todo-1")
        assertTrue(db.listTodos().isEmpty())
        db.close()
    }

    @Test
    fun `concurrent sqlite claims change a pending todo exactly once`() =
        runBlocking {
            val tempDb =
                createTempDirectory("visual-agent-db-todo-claim-test")
                    .resolve("todos.db")
                    .toString()
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create(tempDb)
            db.saveTodo(Todo(id = "todo-claim", description = "Claim me", status = TodoStatus.PENDING))

            val start = CompletableDeferred<Unit>()
            val claims =
                listOf("agent-1", "agent-2")
                    .map { agentId ->
                        async(Dispatchers.Default) {
                            start.await()
                            db.claimPendingTodo("todo-claim", agentId)
                        }
                    }.also { start.complete(Unit) }
                    .awaitAll()
            val claimed = claims.single { it != null }

            assertEquals(TodoStatus.IN_PROGRESS, claimed?.status)
            assertEquals(1, claims.count { it != null })
            assertEquals(claimed?.assignedAgentId, db.listTodos().single().assignedAgentId)
            db.close()
        }

    @Test
    fun `deleted todo snapshot survives active row removal`() {
        val tempDb = createTempDirectory("visual-agent-db-deleted-todo-test").resolve("todos.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val todo = Todo(id = "todo-deleted", description = "Retain this snapshot", status = TodoStatus.COMPLETED)

        db.saveTodo(todo)
        db.deleteTodoAndArchive(todo)

        assertTrue(db.listTodos().isEmpty())
        assertEquals(todo, db.listDeletedTodos().single())
        db.close()
    }
}
