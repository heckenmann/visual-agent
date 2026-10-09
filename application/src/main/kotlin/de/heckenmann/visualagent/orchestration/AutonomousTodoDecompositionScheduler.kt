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
import mu.KotlinLogging
import reactor.core.Disposable
import reactor.core.Disposables
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Schedules complex-todo decomposition without bypassing worker capacity limits. */
internal class AutonomousTodoDecompositionScheduler(
    private val todoStore: TodoStore,
    private val taskPlanner: AutonomousTaskPlanner,
    private val jobScheduler: SubAgentJobScheduler,
    private val subAgentOps: SubAgentOpsProvider,
    private val executionControl: SubAgentExecutionControl?,
    private val signalWork: () -> Unit,
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val activeJobs = ConcurrentHashMap<String, Disposable>()
    private val decomposingTodoIds = ConcurrentHashMap.newKeySet<String>()
    private val attemptedTodoIds = ConcurrentHashMap.newKeySet<String>()
    private val decompositionActive = AtomicBoolean(false)

    fun isDecomposing(todoId: String): Boolean = todoId in decomposingTodoIds

    /** Returns whether a todo has already had a decomposition attempt in this process. */
    fun hasAttemptedDecomposition(todoId: String): Boolean = todoId in attemptedTodoIds

    fun cancel(todoId: String) {
        jobScheduler.cancelQueuedRequest("decomposition:$todoId")
        activeJobs[todoId]?.dispose()
    }

    fun onTodoChanged(change: TodoChange) {
        if (change.type == TodoChangeType.CLEARED) {
            activeJobs.keys.toList().forEach(::cancel)
            attemptedTodoIds.clear()
            return
        }
        val changedId = change.todo?.id ?: change.todoId
        if (changedId != null && change.type != TodoChangeType.REORDERED) cancel(changedId)
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
                        it.decompositionDepth == 0 &&
                        it.id !in attemptedTodoIds &&
                        taskPlanner.isComplex(it.description)
                }
        if (todo == null) {
            decompositionActive.set(false)
            return
        }
        val analyst = taskPlanner.analysisAgent()
        if (analyst == null) {
            attemptedTodoIds += todo.id
            decompositionActive.set(false)
            signalWork()
            return
        }
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
        val job = Disposables.swap()
        activeJobs[todo.id] = job

        fun cleanup(): Mono<Void> =
            Mono
                .fromRunnable<Void> {
                    activeJobs.remove(todo.id, job)
                    decomposingTodoIds.remove(todo.id)
                    releaseAnalyst(analyst, todo.id)
                    decompositionActive.set(false)
                    signalWork()
                }.subscribeOn(Schedulers.boundedElastic())
        val pipeline =
            Mono.usingWhen(
                Mono.just(todo),
                {
                    jobScheduler
                        .runReactive(analyst.id, "decomposition:${todo.id}") {
                            taskPlanner.expandComplexTodo(todo, analyst)
                        }.subscribeOn(Schedulers.boundedElastic())
                },
                { cleanup() },
                { _, _ -> cleanup() },
                { cleanup() },
            )
        job.update(
            pipeline.subscribe({}, { error ->
                if (error !is CancellationException) {
                    logger.warn(
                        error,
                    ) { "Could not decompose todo ${todo.id}; leaving it available for execution" }
                }
            }),
        )
    }

    /** Cancels every registered decomposition and releases its analyst reservation. */
    override fun close() {
        activeJobs.values.forEach(Disposable::dispose)
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
        if (subAgentOps.getSubAgent(analyst.id) !== analyst || analyst.currentTask != decompositionTask(todoId)) return
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
