package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.todo.Todo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies construction of the main-agent review input for terminal todos. */
class AgentManagerTodoReviewTest {
    @Test
    fun `todo review input follows history ending in every supported role`() {
        listOf("user", "assistant", "system").forEach { finalRole ->
            val history =
                appendTodoChangeReviewInput(
                    listOf(Message(finalRole, "Existing message")),
                    Todo(id = "todo-1", description = "Test todo"),
                )
            val reviewContent = history.last().content

            assertEquals("user", history.last().role)
            assertTrue(reviewContent.contains("id=todo-1"))
        }
    }
}
