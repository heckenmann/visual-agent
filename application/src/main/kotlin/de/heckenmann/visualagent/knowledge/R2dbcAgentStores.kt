package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockList
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockNullable
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockRequired
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/** R2DBC implementation for persisted sub-agent runtime state. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcSubAgentStore(
    private val databaseClient: DatabaseClient,
    private val transactionOperator: TransactionalOperator,
) : SubAgentStore {
    override fun saveAgent(agent: PersistedSubAgent): Boolean = saveAgentReactive(agent).blockRequired()

    override fun getAgent(id: String): PersistedSubAgent? = getAgentReactive(id).blockNullable()

    override fun listAgents(status: String?): List<PersistedSubAgent> = listAgentsReactive(status).blockList()

    override fun deleteAgent(id: String): Boolean = deleteAgentReactive(id).blockRequired()

    override fun updateAgentStatus(
        id: String,
        status: String,
        currentTask: String?,
    ): Boolean = updateAgentStatusReactive(id, status, currentTask).blockRequired()

    override fun saveAgentReactive(agent: PersistedSubAgent): Mono<Boolean> =
        databaseClient
            .sql("SELECT created_at FROM sub_agents WHERE id = :id")
            .bind("id", agent.id)
            .map { row, _ -> R2dbcPersistenceSupport.requiredText(row, "created_at") }
            .one()
            .map { it to false }
            .switchIfEmpty(Mono.just(agent.createdAt.toString() to true))
            .flatMap { (createdAt, inserted) ->
                transactionOperator.transactional(
                    databaseClient
                        .sql(
                            """
                            MERGE INTO sub_agents
                                (id, name, role, status, current_task, parent_agent_id, config, created_at, updated_at)
                            KEY (id)
                            VALUES (:id, :name, :role, :status, :currentTask, :parentAgentId, :config, :createdAt, :updatedAt)
                            """.trimIndent(),
                        ).bind("id", agent.id)
                        .bind("name", agent.name)
                        .bind("role", agent.role)
                        .bind("status", agent.status)
                        .let { R2dbcPersistenceSupport.bindText(it, "currentTask", agent.currentTask) }
                        .let { R2dbcPersistenceSupport.bindText(it, "parentAgentId", agent.parentAgentId) }
                        .bind("config", agent.config)
                        .bind("createdAt", createdAt)
                        .bind("updatedAt", agent.updatedAt.toString())
                        .fetch()
                        .rowsUpdated()
                        .thenReturn(inserted),
                )
            }

    override fun getAgentReactive(id: String): Mono<PersistedSubAgent> =
        databaseClient
            .sql(
                "SELECT id, name, role, status, current_task, parent_agent_id, config, created_at, updated_at FROM sub_agents WHERE id = :id",
            ).bind("id", id)
            .map { row, _ -> row.toPersistedSubAgent() }
            .one()

    override fun listAgentsReactive(status: String?): Flux<PersistedSubAgent> {
        val base = "SELECT id, name, role, status, current_task, parent_agent_id, config, created_at, updated_at FROM sub_agents"
        val statement =
            if (status == null) {
                databaseClient.sql("$base ORDER BY created_at DESC, id DESC")
            } else {
                databaseClient.sql("$base WHERE status = :status ORDER BY created_at DESC, id DESC").bind("status", status)
            }
        return statement.map { row, _ -> row.toPersistedSubAgent() }.all()
    }

    override fun deleteAgentReactive(id: String): Mono<Boolean> =
        databaseClient
            .sql("DELETE FROM sub_agents WHERE id = :id")
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .map { it > 0 }

    override fun updateAgentStatusReactive(
        id: String,
        status: String,
        currentTask: String?,
    ): Mono<Boolean> =
        varStatement("UPDATE sub_agents SET status = :status, current_task = :currentTask, updated_at = :updatedAt WHERE id = :id")
            .bind("status", status)
            .let { R2dbcPersistenceSupport.bindText(it, "currentTask", currentTask) }
            .bind("updatedAt", Instant.now().toString())
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .map { it > 0 }

    private fun varStatement(sql: String): DatabaseClient.GenericExecuteSpec = databaseClient.sql(sql)
}

/** R2DBC implementation for the durable main-agent memory document. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcMainAgentLongTermMemoryStore(
    private val databaseClient: DatabaseClient,
    private val transactionOperator: TransactionalOperator,
) : MainAgentLongTermMemoryStore {
    override fun snapshot(): MainAgentLongTermMemory = snapshotReactive().blockRequired()

    override fun replace(
        content: String,
        expectedRevision: Long,
        maxCodePoints: Int,
    ): MainAgentLongTermMemoryEdit = replaceReactive(content, expectedRevision, maxCodePoints).blockRequired()

    override fun snapshotReactive(): Mono<MainAgentLongTermMemory> =
        databaseClient
            .sql("SELECT content, content_length, revision, updated_at FROM main_agent_long_term_memory WHERE scope = 'main'")
            .map { row, _ -> row.toMainAgentMemory() }
            .one()

    override fun replaceReactive(
        content: String,
        expectedRevision: Long,
        maxCodePoints: Int,
    ): Mono<MainAgentLongTermMemoryEdit> {
        require(maxCodePoints > 0) { "Main-agent memory limit must be positive" }
        val size = content.codePointCount(0, content.length)
        require(size <= maxCodePoints) { "Main-agent memory has $size characters; maximum is $maxCodePoints" }
        val updatedAt = Instant.now().toString()
        return transactionOperator.transactional(
            databaseClient
                .sql(
                    """
                    UPDATE main_agent_long_term_memory
                    SET content = :content, content_length = :contentLength,
                        revision = revision + 1, updated_at = :updatedAt
                    WHERE scope = 'main' AND revision = :expectedRevision
                    """.trimIndent(),
                ).bind("content", content)
                .bind("contentLength", size)
                .bind("updatedAt", updatedAt)
                .bind("expectedRevision", expectedRevision)
                .fetch()
                .rowsUpdated()
                .flatMap { updated ->
                    snapshotReactive().map { snapshot ->
                        if (updated == 1L) MainAgentLongTermMemoryEdit.Saved(snapshot) else MainAgentLongTermMemoryEdit.Conflict(snapshot)
                    }
                },
        )
    }
}

private fun io.r2dbc.spi.Row.toPersistedSubAgent(): PersistedSubAgent =
    PersistedSubAgent(
        id = R2dbcPersistenceSupport.requiredText(this, "id"),
        name = R2dbcPersistenceSupport.requiredText(this, "name"),
        role = R2dbcPersistenceSupport.requiredText(this, "role"),
        status = R2dbcPersistenceSupport.requiredText(this, "status"),
        currentTask = R2dbcPersistenceSupport.text(this, "current_task"),
        parentAgentId = R2dbcPersistenceSupport.text(this, "parent_agent_id"),
        config = R2dbcPersistenceSupport.requiredText(this, "config"),
        createdAt = R2dbcPersistenceSupport.instant(this, "created_at") ?: Instant.EPOCH,
        updatedAt = R2dbcPersistenceSupport.instant(this, "updated_at") ?: Instant.EPOCH,
    )

private fun io.r2dbc.spi.Row.toMainAgentMemory(): MainAgentLongTermMemory =
    MainAgentLongTermMemory(
        content = R2dbcPersistenceSupport.requiredText(this, "content"),
        contentLength = R2dbcPersistenceSupport.integer(this, "content_length") ?: 0,
        revision = R2dbcPersistenceSupport.long(this, "revision") ?: 0L,
        updatedAt = R2dbcPersistenceSupport.instant(this, "updated_at") ?: Instant.EPOCH,
    )
