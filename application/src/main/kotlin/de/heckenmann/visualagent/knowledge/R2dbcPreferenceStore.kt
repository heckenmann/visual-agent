package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockCompletion
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockNullable
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.time.Instant

/** R2DBC implementation for application and provider preferences. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcPreferenceStore(
    private val databaseClient: DatabaseClient,
    private val transactionOperator: TransactionalOperator,
) : PreferenceStore,
    ProviderPreferenceStore {
    override fun getPreference(key: String): String? = getPreferenceReactive(key).blockNullable()

    override fun setPreference(
        key: String,
        value: String,
    ) {
        setPreferenceReactive(key, value).blockCompletion()
    }

    override fun getPreferenceReactive(key: String): Mono<String> =
        databaseClient
            .sql(
                """
                SELECT preference_value
                FROM user_preferences
                WHERE preference_key = :key
                """.trimIndent(),
            ).bind("key", key)
            .map { row, _ -> R2dbcPersistenceSupport.text(row, "preference_value").orEmpty() }
            .one()

    override fun setPreferenceReactive(
        key: String,
        value: String,
    ): Mono<Void> =
        transactionOperator.transactional(
            databaseClient
                .sql(
                    """
                    MERGE INTO user_preferences (preference_key, preference_value, preference_type, updated_at)
                    KEY (preference_key)
                    VALUES (:key, :value, 'string', :updatedAt)
                    """.trimIndent(),
                ).bind("key", key)
                .bind("value", value)
                .bind("updatedAt", Instant.now().toString())
                .fetch()
                .rowsUpdated()
                .then(),
        )
}
