package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodoExecutionCandidateTest {
    @Test
    fun `selection uses stable position and id ordering without sorting input`() {
        val worker = SubAgent(id = "worker", name = "Worker", role = "General")
        val laterId = todo(id = "z", position = 1)
        val earlierId = todo(id = "a", position = 1)
        val last = todo(id = "last", position = 2)

        val selected = findNextAssignableTodo(listOf(last, laterId, earlierId), mapOf(worker.id to worker))

        assertEquals(earlierId.id, selected?.todo?.id)
    }

    @Test
    fun `eligibility is evaluated once per idle agent regardless of backlog size`() {
        val first = SubAgent(id = "first", name = "First", role = "General")
        val second = SubAgent(id = "second", name = "Second", role = "General")
        val evaluations = mutableMapOf<String, Int>()

        findNextAssignableTodo(
            todos = (1..100).map { todo(id = "todo-$it", position = it) },
            subAgents = linkedMapOf(first.id to first, second.id to second),
            isAgentEligible = { id ->
                evaluations[id] = evaluations.getOrDefault(id, 0) + 1
                id == second.id
            },
        )

        assertEquals(mapOf("first" to 1, "second" to 1), evaluations)
    }

    @Test
    fun `assigned todo is skipped when its agent is busy`() {
        val busy = SubAgent(id = "busy", name = "Busy", role = "General", status = AgentStatus.BUSY)
        val assigned = todo(id = "assigned", position = 0, assignedAgentId = busy.id)

        assertNull(findNextAssignableTodo(listOf(assigned), mapOf(busy.id to busy)))
    }

    private fun todo(
        id: String,
        position: Int,
        assignedAgentId: String? = null,
    ) = Todo(
        id = id,
        description = id,
        status = TodoStatus.PENDING,
        position = position,
        assignedAgentId = assignedAgentId,
    )
}
