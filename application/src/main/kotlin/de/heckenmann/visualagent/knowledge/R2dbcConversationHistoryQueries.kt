package de.heckenmann.visualagent.knowledge

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Owns conversation-history reads, request replay lookup, and group-aware paging. */
@Component
internal class R2dbcConversationHistoryQueries(
    private val databaseClient: DatabaseClient,
) {
    /** Reads assistant turns belonging to one request in stable chronological order. */
    fun getConversationMessagesForRequest(requestId: String): List<ConversationRecord> =
        getConversationMessagesForRequestReactive(requestId).collectList().block() ?: emptyList()

    /** Reactive counterpart of [getConversationMessagesForRequest]. */
    fun getConversationMessagesForRequestReactive(requestId: String): Flux<ConversationRecord> =
        databaseClient
            .sql(
                """
                SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy,
                       parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id
                FROM conversation_history
                WHERE conversation_request_id = :requestId AND role = 'assistant'
                ORDER BY timeline_sequence ASC, created_at ASC, id ASC
                """.trimIndent(),
            ).bind("requestId", requestId)
            .map { row, _ -> row.toConversationRecord() }
            .all()

    /** Reads recent conversation rows in chronological order. */
    fun getConversationMessages(
        sessionId: String,
        limit: Int,
    ): List<ConversationRecord> = getConversationMessagesReactive(sessionId, limit).collectList().block() ?: emptyList()

    /** Reads the bounded source rows used to assemble main-agent provider context. */
    fun getConversationMessagesForContext(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): List<ConversationRecord> =
        getConversationMessagesForContextReactive(sessionId, userTurnLimit, recordLimit).collectList().block() ?: emptyList()

    /** Reads one raw page in chronological order. */
    fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationRecord> = getConversationMessagesPageReactive(sessionId, limit, offset).collectList().block() ?: emptyList()

    /** Reads a page expanded to include every assistant/tool group it intersects. */
    fun getConversationHistoryPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): ConversationStorePage =
        loadHistoryPageReactive(sessionId, limit, offset).block() ?: ConversationStorePage(emptyList(), offset, false)

    /** Reads the latest page, including complete assistant/tool groups. */
    fun getLatestConversationHistoryPage(
        sessionId: String,
        limit: Int,
    ): ConversationStorePage = getConversationHistoryPage(sessionId, limit, 0)

    /** Searches a bounded set of messages in a session. */
    fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationRecord> = searchConversationMessagesReactive(sessionId, query, limit).collectList().block() ?: emptyList()

    /** Reactive counterpart of [getConversationMessages]. */
    fun getConversationMessagesReactive(
        sessionId: String,
        limit: Int,
    ): Flux<ConversationRecord> =
        databaseClient
            .sql(
                """
                SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy,
                       parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id
                FROM conversation_history
                WHERE session_id = :sessionId
                ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                LIMIT :limit
                """.trimIndent(),
            ).bind("sessionId", sessionId)
            .bind("limit", limit.coerceAtLeast(1))
            .map { row, _ -> row.toConversationRecord() }
            .all()
            .collectList()
            .flatMapMany { Flux.fromIterable(it.asReversed()) }

    /** Reactive counterpart of [getConversationMessagesForContext]. */
    fun getConversationMessagesForContextReactive(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): Flux<ConversationRecord> {
        val turnLimit = userTurnLimit.coerceAtLeast(1)
        val maxRecords = recordLimit.coerceAtLeast(1)
        val dialogue =
            databaseClient
                .sql(
                    """
                    WITH boundary AS (
                        SELECT timeline_sequence, created_at, id
                        FROM conversation_history
                        WHERE session_id = :sessionId AND role = 'user'
                        ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                        LIMIT 1 OFFSET :boundaryOffset
                    )
                    SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy,
                           parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id
                    FROM conversation_history
                    WHERE session_id = :sessionId AND context_policy = 'DIALOGUE'
                      AND (
                          NOT EXISTS (SELECT 1 FROM boundary)
                          OR timeline_sequence > (SELECT timeline_sequence FROM boundary)
                          OR (timeline_sequence = (SELECT timeline_sequence FROM boundary) AND
                              (created_at > (SELECT created_at FROM boundary) OR
                               (created_at = (SELECT created_at FROM boundary) AND id >= (SELECT id FROM boundary))))
                      )
                    ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                    LIMIT :recordLimit
                    """.trimIndent(),
                ).bind("sessionId", sessionId)
                .bind("boundaryOffset", turnLimit - 1)
                .bind("recordLimit", maxRecords)
                .map { row, _ -> row.toConversationRecord() }
                .all()
        val summaries =
            databaseClient
                .sql(
                    """
                    WITH previous_user AS (
                        SELECT timeline_sequence, created_at, id
                        FROM conversation_history
                        WHERE session_id = :sessionId AND role = 'user'
                        ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                        LIMIT 1 OFFSET :turnLimit
                    )
                    SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy,
                           parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id
                    FROM conversation_history
                    WHERE session_id = :sessionId AND context_policy = 'SUMMARY_SOURCE'
                      AND (
                          NOT EXISTS (SELECT 1 FROM previous_user)
                          OR timeline_sequence > (SELECT timeline_sequence FROM previous_user)
                          OR (timeline_sequence = (SELECT timeline_sequence FROM previous_user) AND
                              (created_at > (SELECT created_at FROM previous_user) OR
                               (created_at = (SELECT created_at FROM previous_user) AND id > (SELECT id FROM previous_user))))
                      )
                    ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                    LIMIT :recordLimit
                    """.trimIndent(),
                ).bind("sessionId", sessionId)
                .bind("turnLimit", turnLimit)
                .bind("recordLimit", maxRecords)
                .map { row, _ -> row.toConversationRecord() }
                .all()
        return Flux.concat(dialogue, summaries).collectList().flatMapMany { rows ->
            Flux.fromIterable(
                rows
                    .distinctBy(ConversationRecord::id)
                    .sortedWith(compareBy<ConversationRecord> { it.timelineSequence }.thenBy { it.createdAt }.thenBy { it.id }),
            )
        }
    }

    /** Reactive counterpart of [getConversationMessagesPage]. */
    fun getConversationMessagesPageReactive(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): Flux<ConversationRecord> =
        databaseClient
            .sql(
                """
                SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy,
                       parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id
                FROM conversation_history
                WHERE session_id = :sessionId
                ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """.trimIndent(),
            ).bind("sessionId", sessionId)
            .bind("limit", limit.coerceAtLeast(1))
            .bind("offset", offset.coerceAtLeast(0))
            .map { row, _ -> row.toConversationRecord() }
            .all()
            .collectList()
            .flatMapMany { Flux.fromIterable(it.asReversed()) }

    /** Reactive counterpart of [searchConversationMessages]. */
    fun searchConversationMessagesReactive(
        sessionId: String,
        query: String,
        limit: Int,
    ): Flux<ConversationRecord> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return Flux.empty()
        return databaseClient
            .sql(
                """
                SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy,
                       parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id
                FROM conversation_history
                WHERE session_id = :sessionId AND lower(content) LIKE :query
                ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                LIMIT :limit
                """.trimIndent(),
            ).bind("sessionId", sessionId)
            .bind("query", "%${normalized.lowercase()}%")
            .bind("limit", limit.coerceIn(1, 200))
            .map { row, _ -> row.toConversationRecord() }
            .all()
    }

    private fun loadHistoryPageReactive(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): Mono<ConversationStorePage> {
        val pageSize = limit.coerceAtLeast(1)
        val pageOffset = offset.coerceAtLeast(0)
        return databaseClient
            .sql(
                """
                WITH selected_rows AS (
                    SELECT id FROM conversation_history
                    WHERE session_id = :sessionId
                    ORDER BY timeline_sequence DESC, created_at DESC, id DESC
                    LIMIT :limit OFFSET :offset
                ), turn_roots AS (
                    SELECT id AS turn_id FROM conversation_history
                    WHERE id IN (SELECT id FROM selected_rows) AND role = 'assistant' AND assistant_tool_turn = TRUE
                    UNION
                    SELECT parent_assistant_turn_id AS turn_id FROM conversation_history
                    WHERE id IN (SELECT id FROM selected_rows) AND parent_assistant_turn_id IS NOT NULL
                )
                SELECT id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy,
                       parent_assistant_turn_id, turn_order, assistant_tool_turn, conversation_request_id,
                       (SELECT COUNT(*) FROM selected_rows) AS raw_page_count,
                       (SELECT COUNT(*) FROM conversation_history WHERE session_id = :sessionId) AS total_row_count
                FROM conversation_history
                WHERE id IN (SELECT id FROM selected_rows)
                   OR id IN (SELECT turn_id FROM turn_roots)
                   OR parent_assistant_turn_id IN (SELECT turn_id FROM turn_roots)
                ORDER BY timeline_sequence ASC, created_at ASC, id ASC
                """.trimIndent(),
            ).bind("sessionId", sessionId)
            .bind("limit", pageSize)
            .bind("offset", pageOffset)
            .map { row, _ ->
                PageRecord(
                    record = row.toConversationRecord(),
                    rawPageCount = row.get("raw_page_count")?.toString()?.toIntOrNull() ?: 0,
                    totalRowCount = row.get("total_row_count")?.toString()?.toIntOrNull() ?: 0,
                )
            }.all()
            .collectList()
            .map { rows ->
                val rawCount = rows.firstOrNull()?.rawPageCount ?: 0
                val totalCount = rows.firstOrNull()?.totalRowCount ?: 0
                ConversationStorePage(
                    records = rows.map(PageRecord::record).distinctBy(ConversationRecord::id),
                    nextOffset = pageOffset + rawCount,
                    hasMore = pageOffset + rawCount < totalCount,
                )
            }
    }

    private data class PageRecord(
        val record: ConversationRecord,
        val rawPageCount: Int,
        val totalRowCount: Int,
    )
}
