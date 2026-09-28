package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.Job

/** Applies individual and bulk todo cancellation to the todo, worker, decomposition, and queue. */
internal class AutonomousTodoControl(
    private val todoLifecycleLock: Any,
    private val todoManager: TodoManager,
    private val todoStore: TodoStore,
    private val activeCancellationTokens: Map<String, CancellationToken>,
    private val activeTodoJobs: Map<String, Job>,
    private val jobScheduler: SubAgentJobScheduler,
    private val decompositionScheduler: AutonomousTodoDecompositionScheduler,
    private val subAgents: () -> Map<String, SubAgent>,
    private val agentBusySince: MutableMap<String, Long>,
    private val subAgentOps: SubAgentOpsProvider,
) {
    /** Cancels one unfinished todo and synchronously removes its queued agent request. */
    fun stopTodo(todoId: String): Boolean =
        synchronized(todoLifecycleLock) {
            val todo = todoStore.listTodos().firstOrNull { it.id == todoId } ?: return@synchronized false
            if (todo.status == TodoStatus.COMPLETED || todo.status == TodoStatus.CANCELLED) return@synchronized false
            cancelExecution(todoId)
            cancelTodoAndReleaseAgent(todoId, todoManager::cancelTodo, subAgents(), agentBusySince, subAgentOps)
        }

    /** Cancels all pending or running todos and synchronously removes their queued requests. */
    fun stopAllTodos(): Int =
        synchronized(todoLifecycleLock) {
            val stoppableTodos = todoStore.listTodos().filter { it.status == TodoStatus.PENDING || it.status == TodoStatus.IN_PROGRESS }
            stoppableTodos.forEach { todo ->
                cancelExecution(todo.id)
                cancelTodoAndReleaseAgent(todo.id, todoManager::cancelTodo, subAgents(), agentBusySince, subAgentOps)
            }
            stoppableTodos.size
        }

    private fun cancelExecution(todoId: String) {
        activeCancellationTokens[todoId]?.cancel()
        activeTodoJobs[todoId]?.cancel()
        jobScheduler.cancelQueuedRequest("todo:$todoId")
        decompositionScheduler.cancel(todoId)
    }
}
