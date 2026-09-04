package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentExecutionControl
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Schedules complex-todo decomposition without bypassing worker capacity limits. */
internal class AutonomousTodoDecompositionScheduler(
    private val scope: CoroutineScope,
    private val todoStore: TodoStore,
    private val taskPlanner: AutonomousTaskPlanner,
    private val jobScheduler: SubAgentJobScheduler,
    private val subAgentOps: SubAgentOpsProvider,
    private val executionControl: SubAgentExecutionControl?,
    private val signalWork: () -> Unit,
) {
    private val logger = KotlinLogging.logger {}
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val decomposingTodoIds = ConcurrentHashMap.newKeySet<String>()
    private val attemptedTodoIds = ConcurrentHashMap.newKeySet<String>()
    private val decompositionActive = AtomicBoolean(false)

    fun isDecomposing(todoId: String): Boolean = todoId in decomposingTodoIds

    fun cancel(todoId: String) {
        activeJobs[todoId]?.cancel()
    }

    fun onTodoChanged(change: TodoChange) {
        val todo = change.todo ?: return
        if (todo.status == TodoStatus.PENDING &&
            change.previousStatus == TodoStatus.PENDING &&
            change.type == TodoChangeType.UPDATED
        ) {
            attemptedTodoIds.remove(todo.id)
        }
    }

    fun scheduleIfNeeded(autonomousProcessingEnabled: Boolean) {
        if (!autonomousProcessingEnabled || executionControl?.isExecutionAllowed() == false) return
        if (!decompositionActive.compareAndSet(false, true)) return
        val todo =
            todoStore
                .listTodos()
                .firstOrNull {
                    it.status == TodoStatus.PENDING &&
                        it.id !in attemptedTodoIds &&
                        taskPlanner.isComplex(it.description)
                }
        if (todo == null) {
            decompositionActive.set(false)
            return
        }
        val analyst = taskPlanner.analysisAgent()
        if (analyst.status != AgentStatus.IDLE || executionControl?.isExecutionAllowed(analyst.id) == false) {
            decompositionActive.set(false)
            return
        }
        try {
            reserveAnalyst(analyst, todo.id)
        } catch (error: Throwable) {
            resetAnalystReservation(analyst, todo.id)
            decompositionActive.set(false)
            throw error
        }
        decomposingTodoIds += todo.id
        attemptedTodoIds += todo.id
        val job =
            scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    jobScheduler.run(analyst.id) { taskPlanner.expandComplexTodo(todo, analyst) }
                } catch (error: Throwable) {
                    if (error !is CancellationException) {
                        logger.warn(error) { "Could not decompose todo ${todo.id}; leaving it available for execution" }
                    }
                }
            }
        job.invokeOnCompletion {
            activeJobs.remove(todo.id, job)
            decomposingTodoIds.remove(todo.id)
            decompositionActive.set(false)
            releaseAnalyst(analyst, todo.id)
            signalWork()
        }
        activeJobs[todo.id] = job
        job.start()
    }

    private fun reserveAnalyst(
        analyst: SubAgent,
        todoId: String,
    ) {
        analyst.status = AgentStatus.BUSY
        analyst.currentTodoId = null
        analyst.currentTask = decompositionTask(todoId)
        subAgentOps.saveSubAgent(analyst)
        subAgentOps.notifyAgent(analyst.id, "STATUS:${analyst.status.name}")
    }

    private fun releaseAnalyst(
        analyst: SubAgent,
        todoId: String,
    ) {
        if (analyst.currentTask != decompositionTask(todoId)) return
        analyst.status = AgentStatus.IDLE
        analyst.currentTask = null
        analyst.currentTodoId = null
        subAgentOps.saveSubAgent(analyst)
        subAgentOps.notifyAgent(analyst.id, "STATUS:${analyst.status.name}")
    }

    private fun resetAnalystReservation(
        analyst: SubAgent,
        todoId: String,
    ) {
        if (analyst.currentTask != decompositionTask(todoId)) return
        analyst.status = AgentStatus.IDLE
        analyst.currentTask = null
        analyst.currentTodoId = null
    }

    private fun decompositionTask(todoId: String): String = "Decomposing todo $todoId"
}
