package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises lifecycle compare-and-write guarantees against real H2 transactions. */
@DatabaseTest
class TodoPersistenceGuardsTest {
    @Test
    fun `failed child persistence rolls back parent and already inserted children`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:todo-rollback-${UUID.randomUUID()}").use { db ->
            db.saveTodo(Todo("parent", "Original"))
            val expected = db.listTodos().single()
            val children = listOf(Todo("valid-child", "Valid child"), Todo("x".repeat(256), "Invalid primary key"))
            assertFailsWith<Exception> { db.replaceTodoWithChildren(expected, children) }
            assertEquals(listOf(expected), db.listTodos())
        }
    }

    @Test
    fun `competing identical creation inserts exactly one task`() =
        runBlocking {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:todo-creation-${UUID.randomUUID()}").use { db ->
                val start = CompletableDeferred<Unit>()
                val attempts =
                    listOf("first", "second").map { id ->
                        async(Dispatchers.IO) {
                            start.await()
                            db.createTodoIfAbsent(Todo(id, "Same task"))
                        }
                    }
                start.complete(Unit)
                assertEquals(1, attempts.awaitAll().count { it.created })
                assertEquals(1, db.listTodos().size)
            }
        }

    @Test
    fun `competing mutations from the same snapshot commit exactly once`() =
        runBlocking {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:todo-guards-${UUID.randomUUID()}").use { db ->
                db.saveTodo(Todo("task", "Original"))
                val expected = db.listTodos().single()
                val start = CompletableDeferred<Unit>()
                val results =
                    listOf("First edit", "Second edit").map { description ->
                        async(Dispatchers.IO) {
                            start.await()
                            db.updateTodoIfCurrent(expected, expected.copy(description = description))
                        }
                    }
                start.complete(Unit)
                assertEquals(1, results.awaitAll().count { it })
                assertTrue(db.listTodos().single().description != "Original")
            }
        }

    @Test
    fun `deleted task cannot be recreated by conditional worker completion`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:todo-deletion-${UUID.randomUUID()}").use { db ->
            db.saveTodo(Todo("task", "Original", status = TodoStatus.IN_PROGRESS))
            val expected = db.listTodos().single()
            db.deleteTodo("task")
            assertFalse(db.updateTodoIfCurrent(expected, expected.copy(status = TodoStatus.COMPLETED)))
            assertTrue(db.listTodos().isEmpty())
        }
    }

    @Test
    fun `decomposition rejects stale parent and persists leaf generation`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:todo-decomposition-${UUID.randomUUID()}").use { db ->
            db.saveTodo(Todo("parent", "Original"))
            val stale = db.listTodos().single()
            assertTrue(db.updateTodoIfCurrent(stale, stale.copy(description = "Edited")))
            val child = Todo("child", "Executable leaf", decompositionDepth = 1)
            assertFalse(db.replaceTodoWithChildren(stale, listOf(child)))
            assertEquals(1, db.listTodos().size)
            assertTrue(db.replaceTodoWithChildren(db.listTodos().single(), listOf(child)))
            assertEquals(1, db.listTodos().single { it.id == "child" }.decompositionDepth)
            assertEquals(TodoStatus.CANCELLED, db.listTodos().single { it.id == "parent" }.status)
        }
    }
}
