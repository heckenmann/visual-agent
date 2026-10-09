package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.SubAgentOpsProvider
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.getByIdReactive
import de.heckenmann.visualagent.todo.transitionReactive
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** Isolates synchronous conversation/agent callbacks; todo recovery remains R2DBC-native. */
internal fun cleanupTodoExecution(
    agent: SubAgent,
    todoId: String,
    requestId: String,
    token: CancellationToken,
    cancelledByChange: Boolean,
    isActive: () -> Boolean,
    registration: AutoCloseable,
    watcher: AutoCloseable,
    publishTerminal: () -> Unit,
    activeTokens: ConcurrentHashMap<String, CancellationToken>,
    pendingChanges: MutableMap<String, TodoChange>,
    busySince: MutableMap<String, Long>,
    todoManager: TodoManager,
    conversationOps: ConversationOpsProvider,
    subAgentOps: SubAgentOpsProvider,
    onRetryPending: (String) -> Unit,
): Mono<Void> =
    Mono
        .defer {
            publishTerminal()
            watcher.close()
            registration.close()
            if (!activeTokens.remove(todoId, token) || !isActive()) return@defer Mono.empty()
            if (!cancelledByChange || subAgentOps.getSubAgent(agent.id) !== agent) {
                releaseAutonomousTodoAgent(agent, todoId, busySince, subAgentOps)
                return@defer Mono.empty()
            }
            todoManager.getByIdReactive(todoId).map { Optional.of(it) }.defaultIfEmpty(Optional.empty()).flatMap { latest ->
                var retry: Todo? = null
                Mono
                    .fromRunnable<Void> {
                        handleTodoChangeAfterCancellation(
                            agent = agent,
                            todoId = todoId,
                            pendingTodoChanges = pendingChanges,
                            currentTodo = latest.orElse(null),
                            persistMessage = { conversationOps.persist(it.copy(conversationRequestId = requestId)) },
                            saveAgentToDb = subAgentOps::saveSubAgent,
                            releaseAgent = { worker, id -> releaseAutonomousTodoAgent(worker, id, busySince, subAgentOps) },
                            onDescriptionChanged = { worker, todo ->
                                releaseAutonomousTodoAgent(worker, todo.id, busySince, subAgentOps)
                                retry = todo
                            },
                        )
                    }.subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume { error ->
                        mu.KotlinLogging.logger {}.warn(error) { "Todo $todoId cleanup notification failed" }
                        Mono.empty()
                    }.then(
                        Mono.defer {
                            val expected = retry ?: return@defer Mono.empty<Void>()
                            todoManager
                                .transitionReactive(expected, TodoStatus.PENDING)
                                .publishOn(Schedulers.boundedElastic())
                                .doOnNext { changed -> if (changed && isActive()) onRetryPending(todoId) }
                                .then()
                        },
                    )
            }
        }.subscribeOn(Schedulers.boundedElastic())
