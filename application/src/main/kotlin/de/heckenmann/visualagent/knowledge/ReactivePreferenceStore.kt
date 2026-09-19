package de.heckenmann.visualagent.knowledge

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono

/** Reactive persistence contract for application preferences. */
internal interface ReactivePreferenceStore {
    /** Returns one preference value, or an empty publisher when the key is absent. */
    fun getPreference(key: String): Mono<String>

    /** Inserts or replaces one preference value within a reactive transaction. */
    fun setPreference(
        key: String,
        value: String,
    ): Mono<Void>
}

/** H2/R2DBC adapter for application preferences. */
internal class R2dbcPreferenceStore(
    private val databaseClient: DatabaseClient,
    private val transactionOperator: TransactionalOperator,
) : ReactivePreferenceStore {
    override fun getPreference(key: String): Mono<String> =
        databaseClient
            .sql(
                """
                SELECT preference_value
                FROM user_preferences
                WHERE preference_key = :key
                """.trimIndent(),
            ).bind("key", key)
            .map { row, _ -> row.get("preference_value", String::class.java) ?: "" }
            .one()

    override fun setPreference(
        key: String,
        value: String,
    ): Mono<Void> =
        transactionOperator.transactional(
            databaseClient
                .sql(
                    """
                    MERGE INTO user_preferences (preference_key, preference_value, preference_type, updated_at)
                    KEY (preference_key)
                    VALUES (:key, :value, 'string', CURRENT_TIMESTAMP)
                    """.trimIndent(),
                ).bind("key", key)
                .bind("value", value)
                .fetch()
                .rowsUpdated()
                .then(),
        )
}
