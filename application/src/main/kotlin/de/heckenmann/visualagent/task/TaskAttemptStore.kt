package de.heckenmann.visualagent.task

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/** Durable attempt audit, independent of the todo's atomically accepted result. */
@Repository
class TaskAttemptStore(
    private val database: DatabaseClient,
) {
    /** Upserts one server-generated execution identity; retries cannot duplicate records. */
    fun save(result: TaskAttemptResult): Mono<Void> =
        database
            .sql(
                """
                MERGE INTO task_attempts target
                USING (VALUES (CAST(:execution AS VARCHAR(100)), CAST(:todo AS VARCHAR(100)),
                    CAST(:attempt AS INTEGER), CAST(:created AS VARCHAR(100)), CAST(:result AS VARCHAR(262144))))
                    incoming(execution_id, todo_id, attempt_number, created_at, result_json)
                ON target.execution_id = incoming.execution_id
                WHEN MATCHED AND target.accepted = FALSE THEN UPDATE SET result_json = incoming.result_json
                WHEN NOT MATCHED THEN INSERT (execution_id, todo_id, attempt_number, created_at, result_json)
                    VALUES (incoming.execution_id, incoming.todo_id, incoming.attempt_number, incoming.created_at, incoming.result_json)
                """.trimIndent(),
            ).bind("execution", result.executionId)
            .bind("todo", result.todoId)
            .bind("attempt", result.attempt)
            .bind("created", Instant.now().toString())
            .bind("result", result.copy(accepted = false).encode())
            .fetch()
            .rowsUpdated()
            .then()

    /** Reads bounded attempts in stable order; acceptance comes from the committed todo snapshot. */
    fun list(
        todoId: String,
        offset: Int = 0,
        limit: Int = 10,
    ): Flux<TaskAttemptResult> =
        database
            .sql(
                "SELECT result_json FROM task_attempts WHERE todo_id = :todo " +
                    "ORDER BY created_at DESC, execution_id DESC LIMIT :limit OFFSET :offset",
            ).bind("todo", todoId)
            .bind("limit", limit.coerceIn(1, 20))
            .bind("offset", offset.coerceAtLeast(0))
            .map {
                row,
                _,
                ->
                kotlinx.serialization.json.Json
                    .decodeFromString<TaskAttemptResult>(row.get("result_json", String::class.java)!!)
            }.all()
}
