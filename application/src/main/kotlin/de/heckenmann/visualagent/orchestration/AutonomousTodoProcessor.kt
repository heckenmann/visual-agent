package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentExecutionControl
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.todo.TodoApproval
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoProgressUpdate
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoTerminalReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * Processes a single todo by executing the assigned sub-agent's LLM call,
 * reviewing the result, and handling retries and cancellations.
 *
 * Extracted from [AutonomousCoordinator] to keep file sizes under the LOC limit.
 */
internal suspend fun processTodoWithLLM(
    agent: SubAgent,
    todoId: String,
    taskDescription: String,
    llmProvider: LLMProvider,
    memoryStore: MemoryStore,
    agentToolConfigService: AgentToolConfigService,
    taskPlanner: AutonomousTaskPlanner,
    conversationOps: ConversationOpsProvider,
    todoManager: TodoManager,
    subAgentOps: SubAgentOpsProvider,
    activeCancellationTokens: ConcurrentHashMap<String, CancellationToken>,
    agentBusySince: ConcurrentHashMap<String, Long>,
    pendingTodoChanges: ConcurrentHashMap<String, TodoChange>,
    todoEventBus: TodoEventBus,
    scope: CoroutineScope,
    jobScheduler: SubAgentJobScheduler,
    executionControl: SubAgentExecutionControl? = null,
    cancellationToken: CancellationToken? = null,
    conversationRequestId: String? = null,
    retryDelay: suspend (Long) -> Unit,
) {
    val logger = KotlinLogging.logger {}
    val token = cancellationToken ?: CancellationToken()
    token.throwIfCancelled()
    val requestId = conversationRequestId ?: conversationOps.beginConversationRequest()
    activeCancellationTokens[todoId] = token
    val processingJob = currentCoroutineContext()[Job]
    var executionId = ""
    token.onCancelled { processingJob?.cancel() }
    val watcher = startTodoChangeWatcher(todoId, agent.id, taskDescription, token, todoEventBus)
    var attempt = 0
    val maxRetries = agent.config.maxRetries.coerceAtLeast(1)
    var cancelledByChange = false
    try {
        token.throwIfCancelled()
        if (todoManager.getById(todoId)?.status != TodoStatus.IN_PROGRESS) return
        executionControl?.awaitExecutionAllowed(agent.id)
        while (attempt < maxRetries) {
            try {
                executionId =
                    java.util.UUID
                        .randomUUID()
                        .toString()
                todoEventBus.publishProgress(
                    TodoProgressUpdate(
                        todoId = todoId,
                        executionId = executionId,
                        agentId = agent.id,
                    ),
                )
                executionControl?.awaitExecutionAllowed(agent.id)
                val result =
                    jobScheduler.run(agent.id, "todo:$todoId") {
                        agent.performTodo(
                            todoId,
                            taskDescription,
                            llmProvider,
                            memoryStore,
                            agentToolConfigService.toolsFor(agent),
                            token,
                            requestId = requestId,
                            onChunk = { delta ->
                                todoEventBus.publishProgress(
                                    TodoProgressUpdate(
                                        todoId = todoId,
                                        delta = delta,
                                        executionId = executionId,
                                        agentId = agent.id,
                                    ),
                                )
                            },
                            onStreamReset = {
                                executionId =
                                    java.util.UUID
                                        .randomUUID()
                                        .toString()
                                todoEventBus.publishProgress(
                                    TodoProgressUpdate(
                                        todoId = todoId,
                                        executionId = executionId,
                                        agentId = agent.id,
                                    ),
                                )
                            },
                        )
                    }
                todoEventBus.publishProgress(
                    TodoProgressUpdate(
                        todoId = todoId,
                        completed = true,
                        executionId = executionId,
                        agentId = agent.id,
                        reviewing = true,
                    ),
                )
                executionControl?.awaitExecutionAllowed(agent.id)
                val review =
                    jobScheduler.run(agent.id, "todo:$todoId") {
                        taskPlanner.reviewWorkerResult(
                            todoId,
                            taskDescription,
                            result,
                            token,
                        )
                    }
                if (review.approved) {
                    persistSubAgentMessage(
                        agent = agent,
                        content =
                            "Agent ${agent.name} (${agent.id}) completed todo $todoId.\n\n" +
                                "Result:\n${result.take(2000)}\n\n" +
                                "Use `todos` with `get-result` to read the full stored result.",
                        success = true,
                        persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
                        attempt = attempt + 1,
                        executionId = executionId,
                        todoId = todoId,
                    )
                    todoManager.completeTodo(
                        todoId,
                        TodoApproval(
                            review.feedback,
                            requestId,
                        ),
                    )
                    return
                }
                attempt++
                if (attempt >= maxRetries) {
                    persistSubAgentMessage(
                        agent = agent,
                        content =
                            "Agent ${agent.name} (${agent.id}) stopped todo $todoId. " +
                                "Main review rejected attempt $attempt after the final retry.",
                        success = false,
                        persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
                        attempt = attempt,
                        executionId = executionId,
                        todoId = todoId,
                    )
                    todoManager.cancelTodo(todoId, TodoTerminalReason.REVIEW_REJECTED)
                    return
                }
                persistSubAgentMessage(
                    agent = agent,
                    content = "Main review rejected attempt $attempt for todo $todoId; retrying with the same objective.",
                    success = false,
                    persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
                    attempt = attempt,
                    executionId = executionId,
                    todoId = todoId,
                )
                subAgentOps.notifyAgent(agent.id, "Main review requested retry for todo: $todoId")
            } catch (_: kotlinx.coroutines.CancellationException) {
                cancelledByChange = true
                break
            } catch (_: WorkerReviewFailedException) {
                todoManager.cancelTodo(
                    todoId,
                    TodoTerminalReason.REVIEW_FAILED,
                    "The worker finished, but its result could not be reviewed. No automatic worker retry was performed.",
                )
                return
            } catch (error: Exception) {
                attempt++
                val backoff = 500L * attempt
                val userError = ErrorMessageMapper.map(error)
                logger.warn(error) { "Autonomous todo $todoId failed on attempt $attempt for agent ${agent.id}" }
                if (attempt >= maxRetries) {
                    persistSubAgentMessage(
                        agent = agent,
                        content =
                            "Agent ${agent.name} (${agent.id}) stopped todo $todoId. " +
                                "Failed: ${userError.summary}: ${userError.detail}",
                        success = false,
                        persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
                        attempt = attempt,
                        executionId = executionId,
                        todoId = todoId,
                    )
                    todoManager.cancelTodo(
                        todoId,
                        TodoTerminalReason.RETRIES_EXHAUSTED,
                        "${userError.summary}: ${userError.detail}",
                    )
                    return
                }
                persistSubAgentMessage(
                    agent = agent,
                    content =
                        "Agent ${agent.name} (${agent.id}) failed attempt $attempt for todo $todoId. " +
                            "Retrying after ${backoff}ms: ${userError.summary}: ${userError.detail}",
                    success = false,
                    persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
                    attempt = attempt,
                    executionId = executionId,
                    todoId = todoId,
                )
                retryDelay(backoff)
            }
        }
    } catch (_: kotlinx.coroutines.CancellationException) {
        cancelledByChange = true
    } catch (error: Exception) {
        logger.error(error) { "Autonomous job for todo $todoId crashed unexpectedly" }
        persistSubAgentMessage(
            agent = agent,
            content = "Agent ${agent.name} (${agent.id}) stopped todo $todoId. Crashed unexpectedly",
            success = false,
            persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
            attempt = attempt,
            executionId = executionId,
            todoId = todoId,
        )
        val userError = ErrorMessageMapper.map(error)
        todoManager.cancelTodo(
            todoId,
            TodoTerminalReason.EXECUTION_FAILED,
            "${userError.summary}: ${userError.detail}",
        )
    } finally {
        todoEventBus.publishProgress(
            TodoProgressUpdate(
                todoId = todoId,
                completed = true,
                executionId = executionId,
                agentId = agent.id,
            ),
        )
        watcher.close()
        activeCancellationTokens.remove(todoId, token)
        if (cancelledByChange && scope.isActive) {
            handleTodoChangeAfterCancellation(
                agent = agent,
                todoId = todoId,
                pendingTodoChanges = pendingTodoChanges,
                currentTodo = todoManager.getAll().firstOrNull { it.id == todoId },
                todoManager = todoManager,
                persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
                saveAgentToDb = { subAgentOps.saveSubAgent(it) },
                releaseAgent = { currentAgent, currentTodoId ->
                    releaseAutonomousTodoAgent(currentAgent, currentTodoId, agentBusySince, subAgentOps)
                },
                onDescriptionChanged = { changedAgent, todo ->
                    scope.launch {
                        processTodoWithLLM(
                            changedAgent,
                            todo.id,
                            taskPlanner.buildWorkerInstruction(todo),
                            llmProvider,
                            memoryStore,
                            agentToolConfigService,
                            taskPlanner,
                            conversationOps,
                            todoManager,
                            subAgentOps,
                            activeCancellationTokens,
                            agentBusySince,
                            pendingTodoChanges,
                            todoEventBus,
                            scope,
                            jobScheduler,
                            executionControl,
                            retryDelay = retryDelay,
                        )
                    }
                },
            )
        } else if (scope.isActive) {
            releaseAutonomousTodoAgent(agent, todoId, agentBusySince, subAgentOps)
        }
    }
}
