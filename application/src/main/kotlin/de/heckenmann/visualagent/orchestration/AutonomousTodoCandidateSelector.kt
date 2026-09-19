package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentExecutionControl
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.Todo

/** Selects executable todos while respecting decomposition and agent eligibility. */
internal class AutonomousTodoCandidateSelector(
    private val todoStore: TodoStore,
    private val subAgents: Map<String, SubAgent>,
    private val taskPlanner: AutonomousTaskPlanner,
    private val decompositionScheduler: AutonomousTodoDecompositionScheduler,
    private val executionControl: SubAgentExecutionControl?,
) {
    /** Finds the next pending todo that can be claimed by an eligible idle agent. */
    fun find(requestedTodoId: String? = null): TodoExecutionCandidate? =
        findNextAssignableTodo(
            todoStore
                .listTodos()
                .filterNot {
                    decompositionScheduler.isDecomposing(it.id) || shouldDecomposeBeforeExecution(it, requestedTodoId)
                },
            subAgents,
            requestedTodoId = requestedTodoId,
            isAgentEligible = { agentId -> executionControl?.isExecutionAllowed(agentId) ?: true },
        )

    private fun shouldDecomposeBeforeExecution(
        todo: Todo,
        requestedTodoId: String?,
    ): Boolean =
        requestedTodoId == null &&
            taskPlanner.isComplex(todo.description) &&
            !decompositionScheduler.hasAttemptedDecomposition(todo.id)
}
