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

/** R2DBC implementation for managed workspace metadata. */
@Service
@DependsOn("flywayInitializer")
internal class R2dbcWorkspaceFileStore(
    private val databaseClient: DatabaseClient,
    private val transactionOperator: TransactionalOperator,
) : WorkspaceFileStore {
    override fun saveWorkspaceFile(record: WorkspaceFileRecord) {
        saveWorkspaceFileReactive(record).blockCompletion()
    }

    override fun listWorkspaceFiles(): List<WorkspaceFileRecord> = listWorkspaceFilesReactive().blockList()

    override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = getWorkspaceFileReactive(id).blockNullable()

    override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? =
        getWorkspaceFileByPathReactive(relativePath).blockNullable()

    override fun deleteWorkspaceFile(id: String): Boolean = deleteWorkspaceFileReactive(id).blockRequired()

    override fun saveWorkspaceFileReactive(record: WorkspaceFileRecord): Mono<Void> =
        transactionOperator.transactional(
            databaseClient
                .sql(
                    """
                    MERGE INTO workspace_files
                        (id, relative_path, original_name, mime_type, size_bytes, sha256, extracted_text, imported_at, updated_at)
                    KEY (id)
                    VALUES (:id, :relativePath, :originalName, :mimeType, :sizeBytes, :sha256, :extractedText, :importedAt, :updatedAt)
                    """.trimIndent(),
                ).bind("id", record.id)
                .bind("relativePath", record.relativePath)
                .bind("originalName", record.originalName)
                .bind("mimeType", record.mimeType)
                .bind("sizeBytes", record.sizeBytes)
                .bind("sha256", record.sha256)
                .let { R2dbcPersistenceSupport.bindText(it, "extractedText", record.extractedText) }
                .bind("importedAt", record.importedAt.toString())
                .bind("updatedAt", record.updatedAt.toString())
                .fetch()
                .rowsUpdated()
                .then(),
        )

    override fun listWorkspaceFilesReactive(): Flux<WorkspaceFileRecord> =
        databaseClient
            .sql(
                "SELECT id, relative_path, original_name, mime_type, size_bytes, sha256, extracted_text, imported_at, updated_at FROM workspace_files ORDER BY imported_at DESC, id DESC",
            ).map { row, _ -> row.toWorkspaceFileRecord() }
            .all()

    override fun getWorkspaceFileReactive(id: String): Mono<WorkspaceFileRecord> =
        databaseClient
            .sql(
                "SELECT id, relative_path, original_name, mime_type, size_bytes, sha256, extracted_text, imported_at, updated_at FROM workspace_files WHERE id = :id",
            ).bind("id", id)
            .map { row, _ -> row.toWorkspaceFileRecord() }
            .one()

    override fun getWorkspaceFileByPathReactive(relativePath: String): Mono<WorkspaceFileRecord> =
        databaseClient
            .sql(
                "SELECT id, relative_path, original_name, mime_type, size_bytes, sha256, extracted_text, imported_at, updated_at FROM workspace_files WHERE relative_path = :relativePath",
            ).bind("relativePath", relativePath)
            .map { row, _ -> row.toWorkspaceFileRecord() }
            .one()

    override fun deleteWorkspaceFileReactive(id: String): Mono<Boolean> =
        databaseClient
            .sql("DELETE FROM workspace_files WHERE id = :id")
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .map { it > 0 }
}
