package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockList
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockRequired
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/** R2DBC implementation for long-term memory records. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcMemoryStore(
    private val databaseClient: DatabaseClient,
) : MemoryStore {
    override fun saveMemory(
        content: String,
        tags: List<String>,
    ): String = saveMemoryReactive(content, tags).blockRequired()

    override fun saveStructuredKnowledge(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): String = saveStructuredKnowledgeReactive(subject, summary, nextSteps).blockRequired()

    override fun searchMemories(
        query: String,
        limit: Int,
    ): List<Memory> = searchMemoriesReactive(query, limit).blockList()

    override fun saveMemoryReactive(
        content: String,
        tags: List<String>,
    ): Mono<String> {
        val id = UUID.randomUUID().toString()
        return databaseClient
            .sql(
                """
                INSERT INTO long_term_memory (id, content, embedding, tags, created_at, access_count, last_accessed)
                VALUES (:id, :content, NULL, :tags, :createdAt, 0, NULL)
                """.trimIndent(),
            ).bind("id", id)
            .bind("content", content)
            .bind("tags", tags.joinToString(","))
            .bind("createdAt", Instant.now().toString())
            .fetch()
            .rowsUpdated()
            .thenReturn(id)
    }

    override fun saveStructuredKnowledgeReactive(
        subject: String,
        summary: String,
        nextSteps: String?,
    ): Mono<String> = saveMemoryReactive(Json.encodeToString(StructuredKnowledge(subject, summary, nextSteps)), listOf(subject))

    override fun searchMemoriesReactive(
        query: String,
        limit: Int,
    ): Flux<Memory> =
        databaseClient
            .sql(
                """
                SELECT id, content, tags, created_at
                FROM long_term_memory
                WHERE lower(content) LIKE :query OR lower(tags) LIKE :query
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """.trimIndent(),
            ).bind("query", "%${query.lowercase()}%")
            .bind("limit", limit.coerceAtLeast(1))
            .map { row, _ ->
                Memory(
                    id = R2dbcPersistenceSupport.requiredText(row, "id"),
                    content = R2dbcPersistenceSupport.requiredText(row, "content"),
                    tags =
                        R2dbcPersistenceSupport
                            .text(row, "tags")
                            .orEmpty()
                            .split(",")
                            .filter(String::isNotBlank),
                    createdAt = R2dbcPersistenceSupport.instant(row, "created_at") ?: Instant.EPOCH,
                )
            }.all()
}

@Serializable
private data class StructuredKnowledge(
    val subject: String,
    val summary: String,
    val next_steps: String?,
)
