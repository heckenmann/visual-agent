package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentJobScheduler
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
) {
    val logger = KotlinLogging.logger {}
    val token = CancellationToken()
    activeCancellationTokens[todoId] = token
    val watcher = startTodoChangeWatcher(todoId, agent.id, taskDescription, token, todoEventBus)
    var attempt = 0
    val maxRetries = agent.config.maxRetries.coerceAtLeast(1)
    var cancelledByChange = false
    try {
        delay(300)
        while (attempt < maxRetries) {
            try {
                val result =
                    agent.performTodo(
                        todoId,
                        taskDescription,
                        llmProvider,
                        memoryStore,
                        agentToolConfigService.toolsFor(agent),
                        token,
                    )
                if (
                    taskPlanner.reviewWorkerResult(
                        todoId,
                        taskDescription,
                        result,
                        conversationOps.buildMainSystemContextPrompt(),
                    )
                ) {
                    persistSubAgentMessage(
                        agent = agent,
                        content =
                            "Agent ${agent.name} (${agent.id}) completed todo $todoId.\n\n" +
                                "Result:\n${result.take(2000)}\n\n" +
                                "Use `todos` with `get-result` to read the full stored result.",
                        success = true,
                        persistMessage = { conversationOps.persist(it) },
                    )
                    todoManager.completeTodo(todoId)
                    return
                }
                attempt++
                if (attempt >= maxRetries) {
                    todoManager.cancelTodo(todoId)
                    persistSubAgentMessage(
                        agent = agent,
                        content = "Agent ${agent.name} (${agent.id}) stopped todo $todoId. Main review rejected final result",
                        success = false,
                        persistMessage = { conversationOps.persist(it) },
                    )
                    return
                }
                subAgentOps.notifyAgent(agent.id, "Main review requested retry for todo: $todoId")
            } catch (_: kotlinx.coroutines.CancellationException) {
                cancelledByChange = true
                break
            } catch (error: Exception) {
                attempt++
                val backoff = 500L * attempt
                logger.warn(error) { "Autonomous todo $todoId failed on attempt $attempt for agent ${agent.id}" }
                delay(backoff)
                if (attempt >= maxRetries) {
                    val userError = ErrorMessageMapper.map(error)
                    todoManager.cancelTodo(todoId)
                    persistSubAgentMessage(
                        agent = agent,
                        content =
                            "Agent ${agent.name} (${agent.id}) stopped todo $todoId. " +
                                "Failed: ${userError.summary}: ${userError.detail}",
                        success = false,
                        persistMessage = { conversationOps.persist(it) },
                    )
                    return
                }
            }
        }
    } catch (error: Exception) {
        logger.error(error) { "Autonomous job for todo $todoId crashed unexpectedly" }
        todoManager.cancelTodo(todoId)
        persistSubAgentMessage(
            agent = agent,
            content = "Agent ${agent.name} (${agent.id}) stopped todo $todoId. Crashed unexpectedly",
            success = false,
            persistMessage = { conversationOps.persist(it) },
        )
    } finally {
        watcher.close()
        activeCancellationTokens.remove(todoId)
        agentBusySince.remove(agent.id)
        if (cancelledByChange) {
            handleTodoChangeAfterCancellation(
                agent = agent,
                todoId = todoId,
                pendingTodoChanges = pendingTodoChanges,
                currentTodo = todoManager.getAll().firstOrNull { it.id == todoId },
                todoManager = todoManager,
                persistMessage = { conversationOps.persist(it) },
                saveAgentToDb = { subAgentOps.saveSubAgent(it) },
                notifyAgent = subAgentOps::notifyAgent,
                onDescriptionChanged = { changedAgent, todo ->
                    scope.launch {
                        jobScheduler.run {
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
                            )
                        }
                    }
                },
            )
        } else {
            agent.status = AgentStatus.IDLE
            agent.currentTask = null
            agent.currentTodoId = null
            subAgentOps.saveSubAgent(agent)
            subAgentOps.notifyAgent(agent.id, "STATUS:${agent.status.name}")
        }
    }
}
