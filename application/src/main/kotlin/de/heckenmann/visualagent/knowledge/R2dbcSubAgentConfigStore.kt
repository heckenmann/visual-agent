package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockCompletion
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockList
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockNullable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/** R2DBC implementation for reusable sub-agent configurations. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcSubAgentConfigStore(
    private val databaseClient: DatabaseClient,
    private val transactionOperator: TransactionalOperator,
) : SubAgentConfigStore {
    override fun saveSubAgentConfig(config: SubAgentToolConfig) {
        saveSubAgentConfigReactive(config).blockCompletion()
    }

    override fun getSubAgentConfig(id: String): SubAgentToolConfig? = getSubAgentConfigReactive(id).blockNullable()

    override fun listSubAgentConfigs(): List<SubAgentToolConfig> = listSubAgentConfigsReactive().blockList()

    override fun saveSubAgentConfigReactive(config: SubAgentToolConfig): Mono<Void> =
        databaseClient
            .sql("SELECT created_at FROM sub_agent_configs WHERE id = :id")
            .bind("id", config.id)
            .map { row, _ -> R2dbcPersistenceSupport.requiredText(row, "created_at") }
            .one()
            .defaultIfEmpty(Instant.now().toString())
            .flatMap { createdAt ->
                transactionOperator.transactional(
                    databaseClient
                        .sql(
                            """
                            MERGE INTO sub_agent_configs
                                (id, name, description, model, system_prompt, tools, max_turns, enabled, created_at)
                            KEY (id)
                            VALUES (:id, :name, :description, :model, :systemPrompt, :tools, :maxTurns, :enabled, :createdAt)
                            """.trimIndent(),
                        ).bind("id", config.id)
                        .bind("name", config.name)
                        .bind("description", config.description)
                        .bind("model", config.model)
                        .bind("systemPrompt", config.systemPrompt)
                        .bind("tools", Json.encodeToString(config.tools))
                        .bind("maxTurns", config.maxTurns)
                        .bind("enabled", if (config.enabled) 1 else 0)
                        .bind("createdAt", createdAt)
                        .fetch()
                        .rowsUpdated()
                        .then(),
                )
            }

    override fun getSubAgentConfigReactive(id: String): Mono<SubAgentToolConfig> =
        databaseClient
            .sql("SELECT id, name, description, model, system_prompt, tools, max_turns, enabled FROM sub_agent_configs WHERE id = :id")
            .bind("id", id)
            .map { row, _ -> row.toSubAgentConfig() }
            .one()

    override fun listSubAgentConfigsReactive(): Flux<SubAgentToolConfig> =
        databaseClient
            .sql("SELECT id, name, description, model, system_prompt, tools, max_turns, enabled FROM sub_agent_configs ORDER BY id ASC")
            .map { row, _ -> row.toSubAgentConfig() }
            .all()
}
