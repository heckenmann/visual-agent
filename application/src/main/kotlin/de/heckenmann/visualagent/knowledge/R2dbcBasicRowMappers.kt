package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import io.r2dbc.spi.Row
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Instant

/** Maps R2DBC rows into persistence domain records. */
internal fun Row.toDirectoryGrantRecord(): DirectoryGrantRecord =
    DirectoryGrantRecord(
        id = R2dbcPersistenceSupport.requiredText(this, "id"),
        displayName = R2dbcPersistenceSupport.requiredText(this, "display_name"),
        canonicalRoot = R2dbcPersistenceSupport.text(this, "canonical_root"),
        origin = R2dbcPersistenceSupport.requiredText(this, "origin"),
        mode = R2dbcPersistenceSupport.requiredText(this, "mode"),
        ownerClientId = R2dbcPersistenceSupport.text(this, "owner_client_id"),
        createdAt = R2dbcPersistenceSupport.instant(this, "created_at") ?: Instant.EPOCH,
        updatedAt = R2dbcPersistenceSupport.instant(this, "updated_at") ?: Instant.EPOCH,
    )

/** Maps a workspace-file row into its persistence domain record. */
internal fun Row.toWorkspaceFileRecord(): WorkspaceFileRecord =
    WorkspaceFileRecord(
        id = R2dbcPersistenceSupport.requiredText(this, "id"),
        relativePath = R2dbcPersistenceSupport.requiredText(this, "relative_path"),
        originalName = R2dbcPersistenceSupport.requiredText(this, "original_name"),
        mimeType = R2dbcPersistenceSupport.requiredText(this, "mime_type"),
        sizeBytes = R2dbcPersistenceSupport.long(this, "size_bytes") ?: 0L,
        sha256 = R2dbcPersistenceSupport.requiredText(this, "sha256"),
        extractedText = R2dbcPersistenceSupport.text(this, "extracted_text"),
        importedAt = R2dbcPersistenceSupport.instant(this, "imported_at") ?: Instant.EPOCH,
        updatedAt = R2dbcPersistenceSupport.instant(this, "updated_at") ?: Instant.EPOCH,
    )

/** Maps a sub-agent configuration row into its persistence domain record. */
internal fun Row.toSubAgentConfig(): SubAgentToolConfig =
    SubAgentToolConfig(
        id = R2dbcPersistenceSupport.requiredText(this, "id"),
        name = R2dbcPersistenceSupport.requiredText(this, "name"),
        description = R2dbcPersistenceSupport.requiredText(this, "description"),
        model = R2dbcPersistenceSupport.requiredText(this, "model"),
        systemPrompt = R2dbcPersistenceSupport.requiredText(this, "system_prompt"),
        tools =
            runCatching {
                Json.decodeFromString<List<String>>(
                    R2dbcPersistenceSupport.requiredText(this, "tools"),
                )
            }.getOrElse { emptyList() },
        maxTurns = R2dbcPersistenceSupport.integer(this, "max_turns") ?: 5,
        enabled = R2dbcPersistenceSupport.integer(this, "enabled") != 0,
    )
