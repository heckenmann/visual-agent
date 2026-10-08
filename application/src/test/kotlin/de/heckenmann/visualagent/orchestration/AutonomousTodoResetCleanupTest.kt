package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.SubAgent
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AutonomousTodoResetCleanupTest {
    @Test
    fun `rejected cancellation message still releases worker`() {
        val agent = SubAgent(id = "worker", name = "Worker", role = "Implementation")
        val rejection = CancellationException("Conversation request was invalidated")
        var released: SubAgent? = null

        val thrown =
            assertFailsWith<CancellationException> {
                handleTodoChangeAfterCancellation(
                    agent = agent,
                    todoId = "deleted-todo",
                    pendingTodoChanges = mutableMapOf(),
                    currentTodo = null,
                    persistMessage = { throw rejection },
                    saveAgentToDb = { error("Unexpected save") },
                    releaseAgent = { worker, _ -> released = worker },
                    onDescriptionChanged = { _, _ -> error("Unexpected continuation") },
                )
            }

        assertSame(rejection, thrown)
        assertSame(agent, released)
    }
}
