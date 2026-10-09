package de.heckenmann.visualagent.todo

import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/** Reads a detached persisted execution snapshot without blocking. */
internal fun TodoManager.getByIdReactive(id: String): Mono<Todo> =
    todoStore
        .listTodosReactive()
        .filter { it.id == id }
        .next()
        .map { it.copy() }

/** Applies an ownership-checked execution status and publishes only its committed snapshot. */
internal fun TodoManager.transitionReactive(
    expected: Todo,
    status: TodoStatus,
    reason: TodoTerminalReason? = null,
    detail: String? = null,
    approval: TodoApproval? = null,
): Mono<Boolean> =
    Mono.defer {
        val candidate =
            expected.copy(
                status = status,
                updatedAt = Instant.now(),
                completedAt = if (status == TodoStatus.COMPLETED) Instant.now() else null,
                terminalDetail = if (status == TodoStatus.CANCELLED) detail else null,
            )
        todoStore.updateTodoIfCurrentReactive(expected, candidate).doOnNext { changed ->
            if (changed) {
                publish(
                    TodoChange(
                        TodoChangeType.UPDATED,
                        candidate.copy(),
                        previousStatus = expected.status,
                        terminalReason = reason,
                        terminalDetail = candidate.terminalDetail,
                        approval = approval,
                    ),
                )
            }
        }
    }

/** Replaces an unchanged pending parent using the store's native reactive transaction. */
internal fun TodoManager.replaceWithChildrenReactive(
    expected: Todo,
    descriptions: List<String>,
): Mono<Boolean> =
    todoStore.listTodosReactive().collectList().flatMap { todos ->
        val position = (todos.maxOfOrNull { it.position } ?: -1) + 1
        val children =
            descriptions.mapIndexed { index, description ->
                Todo(
                    UUID.randomUUID().toString(),
                    description,
                    position = position + index,
                    decompositionDepth = expected.decompositionDepth + 1,
                )
            }
        todoStore.replaceTodoWithChildrenReactive(expected, children).flatMap { replaced ->
            if (!replaced) {
                Mono.just(false)
            } else {
                getByIdReactive(expected.id)
                    .doOnNext { parent ->
                        publish(
                            TodoChange(
                                TodoChangeType.UPDATED,
                                parent,
                                previousStatus = expected.status,
                                terminalReason = TodoTerminalReason.DECOMPOSED,
                            ),
                        )
                        children.forEach { publish(TodoChange(TodoChangeType.ADDED, it.copy())) }
                    }.thenReturn(true)
            }
        }
    }
