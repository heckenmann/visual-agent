package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.DirectoryGrantRecord
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryGrantOrigin

/** Converts a persisted grant to its authorization model. */
internal fun DirectoryGrantRecord.toDomain() =
    DirectoryGrant(
        id,
        displayName,
        canonicalRoot,
        DirectoryGrantOrigin.valueOf(origin),
        DirectoryAccessMode.valueOf(mode),
        ownerClientId,
        createdAt,
        updatedAt,
    )

/** Converts a grant to the record stored by the database. */
internal fun DirectoryGrant.toRecord() =
    DirectoryGrantRecord(id, displayName, canonicalRoot, origin.name, mode.name, ownerClientId, createdAt, updatedAt)
