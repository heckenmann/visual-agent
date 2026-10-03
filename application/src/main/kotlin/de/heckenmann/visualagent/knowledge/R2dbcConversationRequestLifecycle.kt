package de.heckenmann.visualagent.knowledge

import kotlinx.coroutines.CancellationException
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Serializes request writes and resets through durable session rows inside the caller's transaction. */
@Component
@DependsOn("flywayInitializer")
internal class R2dbcConversationRequestLifecycle(
    private val databaseClient: DatabaseClient,
) {
    /** Acquires the session write lock and rejects previously invalidated requests. */
    fun guard(
        sessionId: String,
        requestId: String?,
    ): Mono<Void> =
        lock(sessionId).then(
            Mono.defer {
                if (requestId == null) return@defer Mono.empty<Void>()
                databaseClient
                    .sql("SELECT session_id, invalidated FROM conversation_requests WHERE request_id = :requestId")
                    .bind("requestId", requestId)
                    .map { row, _ ->
                        require(row.get("session_id", String::class.java) == sessionId) { "Request belongs to another session" }
                        if (row.get("invalidated", Boolean::class.javaObjectType) == true) {
                            throw CancellationException("Conversation request was invalidated by reset")
                        }
                        true
                    }.one()
                    .switchIfEmpty(
                        databaseClient
                            .sql("INSERT INTO conversation_requests (request_id, session_id) VALUES (:requestId, :sessionId)")
                            .bind("requestId", requestId)
                            .bind("sessionId", sessionId)
                            .fetch()
                            .rowsUpdated()
                            .thenReturn(true),
                    ).then()
            },
        )

    /** Invalidates all registered work while retaining tombstones against late writes and retries. */
    fun invalidate(sessionId: String): Mono<Void> =
        lock(sessionId).then(
            databaseClient
                .sql("UPDATE conversation_requests SET invalidated = TRUE WHERE session_id = :sessionId AND invalidated = FALSE")
                .bind("sessionId", sessionId)
                .fetch()
                .rowsUpdated()
                .then(),
        )

    private fun lock(sessionId: String): Mono<Void> =
        databaseClient
            .sql("MERGE INTO conversation_sessions (session_id) KEY (session_id) VALUES (:sessionId)")
            .bind("sessionId", sessionId)
            .fetch()
            .rowsUpdated()
            .then()
}
