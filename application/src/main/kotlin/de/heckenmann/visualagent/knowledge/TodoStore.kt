package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.todo.Todo
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Stores and retrieves todo domain objects. Use cases: UC-0000013, UC-0000014. */
interface TodoStore {
    /** Updates an existing row only if its execution-relevant snapshot is unchanged. */
    fun updateTodoIfCurrent(
        expected: Todo,
        updated: Todo,
    ): Boolean =
        synchronized(this) {
            val current = listTodos().firstOrNull { it.id == expected.id } ?: return@synchronized false
            if (current.copy(position = expected.position) != expected) return@synchronized false
            saveTodo(updated.copy(position = current.position))
            true
        }

    /** Atomically replaces the unchanged pending parent with bounded child tasks. */
    fun replaceTodoWithChildren(
        expected: Todo,
        children: List<Todo>,
    ): Boolean =
        synchronized(this) {
            val current = listTodos().firstOrNull { it.id == expected.id } ?: return@synchronized false
            if (current != expected || current.status != de.heckenmann.visualagent.todo.TodoStatus.PENDING) return@synchronized false
            saveTodo(current.copy(status = de.heckenmann.visualagent.todo.TodoStatus.CANCELLED))
            children.forEach(::saveTodo)
            true
        }

    /** Applies a conditional execution transition without blocking the caller. */
    fun updateTodoIfCurrentReactive(
        expected: Todo,
        updated: Todo,
    ): Mono<Boolean> = Mono.fromCallable { updateTodoIfCurrent(expected, updated) }

    /** Replaces a pending parent and creates its children within one reactive transaction. */
    fun replaceTodoWithChildrenReactive(
        expected: Todo,
        children: List<Todo>,
    ): Mono<Boolean> = Mono.fromCallable { replaceTodoWithChildren(expected, children) }

    /** Inserts or replaces a todo. */
    fun saveTodo(todo: Todo)

    /**
     * Atomically assigns and claims a pending todo for execution.
     *
     * @return The claimed todo, or `null` when it is no longer pending
     */
    fun claimPendingTodo(
        todoId: String,
        agentId: String,
    ): Todo?

    /**
     * Persists list positions without recording new timeline activity.
     *
     * @param todos Todos whose positions changed through a user-initiated reorder
     */
    fun updateTodoPositions(todos: List<Todo>) {
        todos.forEach(::saveTodo)
    }

    /** Creates a todo only when no normalized description already exists. */
    fun createTodoIfAbsent(todo: Todo): TodoCreation

    /** Returns all persisted todos. */
    fun listTodos(): List<Todo>

    /** Deletes one todo by identifier. */
    fun deleteTodo(todoId: String)

    /** Archives a deleted todo snapshot and removes the active row atomically. */
    fun deleteTodoAndArchive(todo: Todo): Todo {
        deleteTodo(todo.id)
        return todo
    }

    /** Returns deleted todo snapshots that may still be shown in conversation history. */
    fun listDeletedTodos(limit: Int = 100): List<Todo> = emptyList()

    /** Deletes every persisted todo. */
    fun clearTodos()

    /** Reactive counterpart of [saveTodo]. */
    fun saveTodoReactive(todo: Todo): Mono<Void> = Mono.fromRunnable { saveTodo(todo) }

    /** Reactive counterpart of [claimPendingTodo]. */
    fun claimPendingTodoReactive(
        todoId: String,
        agentId: String,
    ): Mono<Todo> = Mono.fromCallable { claimPendingTodo(todoId, agentId) }

    /** Reactive counterpart of [updateTodoPositions]. */
    fun updateTodoPositionsReactive(todos: List<Todo>): Mono<Void> = Mono.fromRunnable { updateTodoPositions(todos) }

    /** Reactive counterpart of [createTodoIfAbsent]. */
    fun createTodoIfAbsentReactive(todo: Todo): Mono<TodoCreation> = Mono.fromCallable { createTodoIfAbsent(todo) }

    /** Reactive counterpart of [listTodos]. */
    fun listTodosReactive(): Flux<Todo> = Flux.defer { Flux.fromIterable(listTodos()) }

    /** Reactive counterpart of [deleteTodo]. */
    fun deleteTodoReactive(todoId: String): Mono<Void> = Mono.fromRunnable { deleteTodo(todoId) }

    /** Reactive counterpart of [deleteTodoAndArchive]. */
    fun deleteTodoAndArchiveReactive(todo: Todo): Mono<Todo> = Mono.fromCallable { deleteTodoAndArchive(todo) }

    /** Reactive counterpart of [listDeletedTodos]. */
    fun listDeletedTodosReactive(limit: Int = 100): Flux<Todo> = Flux.defer { Flux.fromIterable(listDeletedTodos(limit)) }

    /** Reactive counterpart of [clearTodos]. */
    fun clearTodosReactive(): Mono<Void> = Mono.fromRunnable { clearTodos() }
}

/** Result of an atomic todo creation attempt. */
data class TodoCreation(
    val todo: Todo,
    val created: Boolean,
)
