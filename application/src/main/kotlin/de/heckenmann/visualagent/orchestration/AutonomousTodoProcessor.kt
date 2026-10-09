package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentExecutionControl
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolExecutionScope
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoApproval
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoProgressUpdate
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoTerminalReason
import de.heckenmann.visualagent.todo.getByIdReactive
import de.heckenmann.visualagent.todo.transitionReactive
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

/** Reactor-native execution, review, retry and ownership-safe cleanup of one claimed todo. */
internal class AutonomousTodoProcessor(
    private val agent: SubAgent,
    private val todoId: String,
    private val taskDescription: String,
    private val claimedTodo: Todo,
    private val llmProvider: LLMProvider,
    private val memoryStore: MemoryStore,
    private val agentToolConfigService: AgentToolConfigService,
    private val taskPlanner: AutonomousTaskPlanner,
    private val conversationOps: ConversationOpsProvider,
    private val todoManager: TodoManager,
    private val subAgentOps: SubAgentOpsProvider,
    private val activeCancellationTokens: ConcurrentHashMap<String, CancellationToken>,
    private val agentBusySince: ConcurrentHashMap<String, Long>,
    private val pendingTodoChanges: ConcurrentHashMap<String, TodoChange>,
    private val todoEventBus: TodoEventBus,
    private val isActive: () -> Boolean,
    private val jobScheduler: SubAgentJobScheduler,
    private val executionControl: SubAgentExecutionControl? = null,
    private val cancellationToken: CancellationToken? = null,
    private val conversationRequestId: String? = null,
    private val retryDelay: (Long) -> Mono<Void>,
    private val toolScopes: () -> ToolExecutionScope = ::ToolExecutionScope,
    private val onRetryPending: (String) -> Unit = {},
    private val onCleanup: () -> Unit = {},
    private val withLifecycleLock: (() -> Unit) -> Unit = { it() },
) {
    private val logger = KotlinLogging.logger {}
    private val token = cancellationToken ?: CancellationToken()
    private var requestId = ""
    private var executionId = ""
    private var attempt = 0
    private var retryFeedback: String? = null
    private var cancelledByChange = false
    private var watcher: AutoCloseable? = null
    private val maxRetries = agent.config.maxRetries.coerceAtLeast(1)

    /** Subscribes one ownership-checked execution with asynchronous terminal cleanup. */
    fun execute(): Mono<Void> =
        Mono.defer {
            if (activeCancellationTokens[todoId] !== token) return@defer Mono.empty()
            requestId = conversationRequestId ?: conversationOps.beginConversationRequest()
            val changeWatcher =
                startTodoChangeWatcher(todoId, agent.id, claimedTodo.description, token, todoEventBus, claimedTodo.timelineSequence)
            watcher = changeWatcher
            val cancelled = Sinks.one<Void>()
            val registration = token.onCancelled { cancelled.tryEmitError(CancellationException("Todo execution cancelled")) }
            Mono.usingWhen(
                Mono.just(changeWatcher),
                {
                    Mono
                        .firstWithSignal(
                            todoManager
                                .getByIdReactive(todoId)
                                .map {
                                    it.copy(position = claimedTodo.position) == claimedTodo
                                }.defaultIfEmpty(false)
                                .publishOn(Schedulers.boundedElastic())
                                .flatMap { current ->
                                    if (!current) {
                                        cancelledByChange = true
                                        Mono.empty()
                                    } else {
                                        runAttempt()
                                    }
                                },
                            cancelled.asMono(),
                        ).publishOn(Schedulers.boundedElastic())
                        .onErrorResume { error ->
                            if (error is CancellationException) {
                                cancelledByChange = true
                                Mono.empty()
                            } else {
                                unexpectedFailure(error)
                            }
                        }
                },
                { cleanup(registration, it) },
                { resource, _ -> cleanup(registration, resource) },
                { resource ->
                    cancelledByChange = true
                    cleanup(registration, resource)
                },
            )
        }

    private fun allowed(): Mono<Void> = executionControl?.executionAllowed(agent.id) ?: Mono.empty()

    private fun runAttempt(): Mono<Void> =
        Mono.defer {
            token.throwIfCancelled()
            val tools = toolScopes()
            Mono
                .using(
                    { tools },
                    {
                        allowed()
                            .publishOn(Schedulers.boundedElastic())
                            .then(
                                Mono.defer {
                                    resetProgress()
                                    jobScheduler.runReactive(agent.id, "todo:$todoId") {
                                        agent.performTodoReactive(
                                            todoId,
                                            taskDescription +
                                                retryFeedback
                                                    ?.let {
                                                        "\n\nReview correction: $it\nInspect prior work; correct the missing parts without repeating successful side effects."
                                                    }.orEmpty(),
                                            llmProvider,
                                            memoryStore,
                                            agentToolConfigService.toolsFor(agent),
                                            token,
                                            requestId = requestId,
                                            toolScope = tools,
                                            onChunk = { delta -> publishProgress(delta = delta) },
                                            onStreamReset = ::resetProgress,
                                        )
                                    }
                                },
                            ).flatMap { result ->
                                publishProgress(completed = true, reviewing = true)
                                allowed()
                                    .then(
                                        jobScheduler.runReactive(agent.id, "todo:$todoId") {
                                            taskPlanner.reviewWorkerResult(todoId, taskDescription, result, token, tools.evidence())
                                        },
                                    ).publishOn(Schedulers.boundedElastic())
                                    .flatMap { review -> handleReview(review, result) }
                            }
                    },
                    ToolExecutionScope::close,
                ).publishOn(Schedulers.boundedElastic())
                .onErrorResume { error ->
                    when (error) {
                        is CancellationException -> Mono.error(error)
                        is WorkerReviewFailedException ->
                            cancel(
                                TodoTerminalReason.REVIEW_FAILED,
                                "The worker finished, but its result could not be reviewed. No automatic worker retry was performed.",
                            )
                        else -> retryFailure(error)
                    }
                }
        }

    private fun handleReview(
        review: WorkerReviewResult,
        result: String,
    ): Mono<Void> =
        Mono.defer {
            token.throwIfCancelled()
            if (review.approved) {
                watcher?.close()
                todoManager
                    .transitionReactive(
                        claimedTodo,
                        TodoStatus.COMPLETED,
                        TodoTerminalReason.COMPLETED,
                        approval = TodoApproval(review.feedback, requestId),
                    ).publishOn(Schedulers.boundedElastic())
                    .doOnNext { completed ->
                        if (!completed) {
                            cancelledByChange = true
                        } else {
                            persistTodoCompletion(agent, todoId, result, requestId, attempt + 1, executionId, conversationOps::persist)
                        }
                    }.then()
            } else {
                retryFeedback = review.feedback
                attempt++
                if (attempt >= maxRetries) {
                    persist(
                        "Agent ${agent.name} (${agent.id}) stopped todo $todoId. Main review rejected attempt $attempt after the final retry.",
                    )
                    cancel(TodoTerminalReason.REVIEW_REJECTED)
                } else {
                    persist("Main review rejected attempt $attempt for todo $todoId: ${review.feedback}. Retrying with the same objective.")
                    subAgentOps.notifyAgent(agent.id, "Main review requested retry for todo: $todoId")
                    runAttempt()
                }
            }
        }

    private fun retryFailure(error: Throwable): Mono<Void> =
        Mono.defer {
            attempt++
            val userError = ErrorMessageMapper.map(error)
            logger.warn(error) { "Autonomous todo $todoId failed on attempt $attempt for agent ${agent.id}" }
            if (attempt >= maxRetries) {
                persist("Agent ${agent.name} (${agent.id}) stopped todo $todoId. Failed: ${userError.summary}: ${userError.detail}")
                cancel(TodoTerminalReason.RETRIES_EXHAUSTED, "${userError.summary}: ${userError.detail}")
            } else {
                val backoff = 500L * attempt
                persist(
                    "Agent ${agent.name} (${agent.id}) failed attempt $attempt for todo $todoId. " +
                        "Retrying after ${backoff}ms: ${userError.summary}: ${userError.detail}",
                )
                retryDelay(backoff).then(Mono.defer { runAttempt() })
            }
        }

    private fun unexpectedFailure(error: Throwable): Mono<Void> =
        Mono.defer {
            logger.error(error) { "Autonomous job for todo $todoId crashed unexpectedly" }
            persist("Agent ${agent.name} (${agent.id}) stopped todo $todoId. Crashed unexpectedly")
            val userError = ErrorMessageMapper.map(error)
            cancel(TodoTerminalReason.EXECUTION_FAILED, "${userError.summary}: ${userError.detail}")
        }

    private fun cancel(
        reason: TodoTerminalReason,
        detail: String? = null,
    ): Mono<Void> =
        Mono.defer {
            watcher?.close()
            todoManager.transitionReactive(claimedTodo, TodoStatus.CANCELLED, reason, detail).then()
        }

    private fun persist(content: String) {
        persistSubAgentMessage(
            agent = agent,
            content = content,
            success = false,
            persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
            attempt = attempt,
            executionId = executionId,
            todoId = todoId,
        )
    }

    private fun resetProgress() {
        executionId =
            java.util.UUID
                .randomUUID()
                .toString()
        publishProgress()
    }

    private fun publishProgress(
        delta: String = "",
        completed: Boolean = false,
        reviewing: Boolean = false,
    ) {
        todoEventBus.publishProgress(
            TodoProgressUpdate(
                todoId = todoId,
                delta = delta,
                completed = completed,
                executionId = executionId,
                agentId = agent.id,
                reviewing = reviewing,
            ),
        )
    }

    private fun cleanup(
        registration: AutoCloseable,
        watcher: AutoCloseable,
    ): Mono<Void> =
        cleanupTodoExecution(
            agent,
            todoId,
            requestId,
            token,
            cancelledByChange,
            isActive,
            registration,
            watcher,
            { publishProgress(completed = true) },
            activeCancellationTokens,
            pendingTodoChanges,
            agentBusySince,
            todoManager,
            conversationOps,
            subAgentOps,
            onRetryPending,
            withLifecycleLock,
        ).doFinally { onCleanup() }
}
