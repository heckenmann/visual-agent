package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockCompletion
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockList
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockNullable
import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockRequired
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** R2DBC implementation for persisted directory access grants. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcDirectoryGrantStore(
    private val databaseClient: DatabaseClient,
    private val transactionOperator: TransactionalOperator,
) : DirectoryGrantStore {
    override fun saveDirectoryGrant(record: DirectoryGrantRecord) {
        saveDirectoryGrantReactive(record).blockCompletion()
    }

    override fun listDirectoryGrants(): List<DirectoryGrantRecord> = listDirectoryGrantsReactive().blockList()

    override fun getDirectoryGrant(id: String): DirectoryGrantRecord? = getDirectoryGrantReactive(id).blockNullable()

    override fun getDirectoryGrantByCanonicalRoot(canonicalRoot: String): DirectoryGrantRecord? =
        getDirectoryGrantByCanonicalRootReactive(canonicalRoot).blockNullable()

    override fun deleteDirectoryGrant(id: String): Boolean = deleteDirectoryGrantReactive(id).blockRequired()

    override fun saveDirectoryGrantReactive(record: DirectoryGrantRecord): Mono<Void> =
        transactionOperator.transactional(
            databaseClient
                .sql(
                    """
                    MERGE INTO directory_grants
                        (id, display_name, canonical_root, origin, mode, owner_client_id, created_at, updated_at)
                    KEY (id)
                    VALUES (:id, :displayName, :canonicalRoot, :origin, :mode, :ownerClientId, :createdAt, :updatedAt)
                    """.trimIndent(),
                ).bind("id", record.id)
                .bind("displayName", record.displayName)
                .let { R2dbcPersistenceSupport.bindText(it, "canonicalRoot", record.canonicalRoot) }
                .bind("origin", record.origin)
                .bind("mode", record.mode)
                .let { R2dbcPersistenceSupport.bindText(it, "ownerClientId", record.ownerClientId) }
                .bind("createdAt", record.createdAt.toString())
                .bind("updatedAt", record.updatedAt.toString())
                .fetch()
                .rowsUpdated()
                .then(),
        )

    override fun listDirectoryGrantsReactive(): Flux<DirectoryGrantRecord> =
        databaseClient
            .sql(
                """
                SELECT id, display_name, canonical_root, origin, mode, owner_client_id, created_at, updated_at
                FROM directory_grants
                ORDER BY created_at ASC, id ASC
                """.trimIndent(),
            ).map { row, _ -> row.toDirectoryGrantRecord() }
            .all()

    override fun getDirectoryGrantReactive(id: String): Mono<DirectoryGrantRecord> =
        databaseClient
            .sql(
                "SELECT id, display_name, canonical_root, origin, mode, owner_client_id, created_at, updated_at FROM directory_grants WHERE id = :id",
            ).bind("id", id)
            .map { row, _ -> row.toDirectoryGrantRecord() }
            .one()

    override fun getDirectoryGrantByCanonicalRootReactive(canonicalRoot: String): Mono<DirectoryGrantRecord> =
        databaseClient
            .sql(
                "SELECT id, display_name, canonical_root, origin, mode, owner_client_id, created_at, updated_at FROM directory_grants WHERE canonical_root = :canonicalRoot",
            ).bind("canonicalRoot", canonicalRoot)
            .map { row, _ -> row.toDirectoryGrantRecord() }
            .one()

    override fun deleteDirectoryGrantReactive(id: String): Mono<Boolean> =
        databaseClient
            .sql("DELETE FROM directory_grants WHERE id = :id")
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .map { it > 0 }
}
