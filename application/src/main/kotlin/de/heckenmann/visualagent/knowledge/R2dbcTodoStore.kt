package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockCompletion
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockList
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockNullable
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockRequired
import de.heckenmann.visualagent.todo.Todo
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/** R2DBC implementation for active and deleted todo records. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcTodoStore(
    private val databaseClient: DatabaseClient,
    private val sequenceStore: R2dbcTimelineSequenceStore,
    private val transactionOperator: TransactionalOperator,
) : TodoStore {
    override fun saveTodo(todo: Todo) {
        saveTodoReactive(todo).blockCompletion()
    }

    override fun claimPendingTodo(
        todoId: String,
        agentId: String,
    ): Todo? = claimPendingTodoReactive(todoId, agentId).blockNullable()

    override fun updateTodoPositions(todos: List<Todo>) {
        updateTodoPositionsReactive(todos).blockCompletion()
    }

    override fun createTodoIfAbsent(todo: Todo): TodoCreation = createTodoIfAbsentReactive(todo).blockRequired()

    override fun listTodos(): List<Todo> = listTodosReactive().blockList()

    override fun deleteTodo(todoId: String) {
        deleteTodoReactive(todoId).blockCompletion()
    }

    override fun deleteTodoAndArchive(todo: Todo): Todo = deleteTodoAndArchiveReactive(todo).blockRequired()

    override fun listDeletedTodos(limit: Int): List<Todo> = listDeletedTodosReactive(limit).blockList()

    override fun clearTodos() {
        clearTodosReactive().blockCompletion()
    }

    override fun saveTodoReactive(todo: Todo): Mono<Void> =
        transactionOperator.transactional(
            sequenceStore.nextReactive().flatMap { sequence ->
                todo.timelineSequence = sequence
                mergeTodo(todo).then()
            },
        )

    override fun claimPendingTodoReactive(
        todoId: String,
        agentId: String,
    ): Mono<Todo> =
        transactionOperator.transactional(
            sequenceStore.nextReactive().flatMap { sequence ->
                databaseClient
                    .sql(
                        """
                        UPDATE todos
                        SET status = 'IN_PROGRESS', assigned_agent_id = :agentId,
                            updated_at = :updatedAt, timeline_sequence = :timelineSequence
                        WHERE id = :todoId AND status = 'PENDING'
                        """.trimIndent(),
                    ).bind("agentId", agentId)
                    .bind("updatedAt", Instant.now().toString())
                    .bind("timelineSequence", sequence)
                    .bind("todoId", todoId)
                    .fetch()
                    .rowsUpdated()
                    .flatMapMany { updated -> if (updated == 1L) selectTodo(todoId) else Flux.empty() }
                    .singleOrEmpty()
            },
        )

    override fun updateTodoPositionsReactive(todos: List<Todo>): Mono<Void> {
        if (todos.isEmpty()) return Mono.empty()
        return transactionOperator.transactional(
            Flux
                .fromIterable(todos)
                .concatMap { todo ->
                    databaseClient
                        .sql("UPDATE todos SET position = :position WHERE id = :id")
                        .bind("position", todo.position)
                        .bind("id", todo.id)
                        .fetch()
                        .rowsUpdated()
                }.then(),
        )
    }

    override fun createTodoIfAbsentReactive(todo: Todo): Mono<TodoCreation> =
        listTodosReactive()
            .filter { normalizeDescription(it.description) == normalizeDescription(todo.description) }
            .next()
            .map { TodoCreation(it, created = false) }
            .switchIfEmpty(
                transactionOperator.transactional(
                    sequenceStore.nextReactive().flatMap { sequence ->
                        todo.timelineSequence = sequence
                        mergeTodo(todo).thenReturn(TodoCreation(todo, created = true))
                    },
                ),
            )

    override fun listTodosReactive(): Flux<Todo> =
        databaseClient
            .sql(
                "SELECT id, description, status, position, assigned_agent_id, created_at, updated_at, timeline_sequence, completed_at, due_date, terminal_detail FROM todos ORDER BY position ASC, id ASC",
            ).map { row, _ -> row.toTodo() }
            .all()

    override fun deleteTodoReactive(todoId: String): Mono<Void> =
        databaseClient
            .sql("DELETE FROM todos WHERE id = :id")
            .bind("id", todoId)
            .fetch()
            .rowsUpdated()
            .then()

    override fun deleteTodoAndArchiveReactive(todo: Todo): Mono<Todo> =
        transactionOperator.transactional(
            sequenceStore.nextReactive().flatMap { sequence ->
                todo.timelineSequence = sequence
                insertDeletedTodo(todo)
                    .then(
                        databaseClient
                            .sql("DELETE FROM todos WHERE id = :id")
                            .bind("id", todo.id)
                            .fetch()
                            .rowsUpdated()
                            .then(),
                    ).thenReturn(todo)
            },
        )

    override fun listDeletedTodosReactive(limit: Int): Flux<Todo> =
        databaseClient
            .sql(
                "SELECT id, description, status, position, assigned_agent_id, created_at, updated_at, timeline_sequence, completed_at, due_date, terminal_detail FROM deleted_todos ORDER BY updated_at DESC, id DESC LIMIT :limit",
            ).bind("limit", limit.coerceIn(1, 100))
            .map { row, _ -> row.toTodo() }
            .all()

    override fun clearTodosReactive(): Mono<Void> =
        transactionOperator.transactional(
            databaseClient.sql("DELETE FROM todos").fetch().rowsUpdated().then(
                databaseClient
                    .sql("DELETE FROM deleted_todos")
                    .fetch()
                    .rowsUpdated()
                    .then(),
            ),
        )

    private fun mergeTodo(todo: Todo): Mono<Long> {
        var statement =
            databaseClient
                .sql(
                    """
                    MERGE INTO todos
                        (id, description, status, position, assigned_agent_id, created_at, updated_at,
                         timeline_sequence, completed_at, due_date, terminal_detail)
                    KEY (id)
                    VALUES (:id, :description, :status, :position, :assignedAgentId, :createdAt, :updatedAt,
                            :timelineSequence, :completedAt, :dueDate, :terminalDetail)
                    """.trimIndent(),
                ).bind("id", todo.id)
                .bind("description", todo.description)
                .bind("status", todo.status.name)
                .bind("position", todo.position)
        statement = R2dbcPersistenceSupport.bindText(statement, "assignedAgentId", todo.assignedAgentId)
        statement = R2dbcPersistenceSupport.bindText(statement, "completedAt", todo.completedAt?.toString())
        statement = R2dbcPersistenceSupport.bindText(statement, "dueDate", todo.dueDate?.toString())
        statement = R2dbcPersistenceSupport.bindText(statement, "terminalDetail", todo.terminalDetail)
        return statement
            .bind("createdAt", todo.createdAt.toString())
            .bind("updatedAt", todo.updatedAt.toString())
            .bind("timelineSequence", todo.timelineSequence)
            .fetch()
            .rowsUpdated()
    }

    private fun insertDeletedTodo(todo: Todo): Mono<Long> {
        var statement =
            databaseClient
                .sql(
                    """
                    MERGE INTO deleted_todos
                        (id, description, status, position, assigned_agent_id, created_at, updated_at,
                         timeline_sequence, completed_at, due_date, terminal_detail)
                    KEY (id)
                    VALUES (:id, :description, :status, :position, :assignedAgentId, :createdAt, :updatedAt,
                            :timelineSequence, :completedAt, :dueDate, :terminalDetail)
                    """.trimIndent(),
                ).bind("id", todo.id)
                .bind("description", todo.description)
                .bind("status", todo.status.name)
                .bind("position", todo.position)
        statement = R2dbcPersistenceSupport.bindText(statement, "assignedAgentId", todo.assignedAgentId)
        statement = R2dbcPersistenceSupport.bindText(statement, "completedAt", todo.completedAt?.toString())
        statement = R2dbcPersistenceSupport.bindText(statement, "dueDate", todo.dueDate?.toString())
        statement = R2dbcPersistenceSupport.bindText(statement, "terminalDetail", todo.terminalDetail)
        return statement
            .bind("createdAt", todo.createdAt.toString())
            .bind("updatedAt", todo.updatedAt.toString())
            .bind("timelineSequence", todo.timelineSequence)
            .fetch()
            .rowsUpdated()
    }

    private fun selectTodo(id: String): Flux<Todo> =
        databaseClient
            .sql(
                "SELECT id, description, status, position, assigned_agent_id, created_at, updated_at, timeline_sequence, completed_at, due_date, terminal_detail FROM todos WHERE id = :id",
            ).bind("id", id)
            .map { row, _ -> row.toTodo() }
            .all()

    private fun normalizeDescription(description: String): String = description.trim().replace(Regex("\\s+"), " ").lowercase()
}
