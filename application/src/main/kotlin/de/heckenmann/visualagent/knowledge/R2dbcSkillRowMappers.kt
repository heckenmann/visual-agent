package de.heckenmann.visualagent.knowledge

import io.r2dbc.spi.Row
import java.time.Instant

/** Maps a database row into a persisted skill. */
internal fun Row.toSkillRecord(): SkillRecord =
    SkillRecord(
        id = R2dbcPersistenceSupport.requiredText(this, "id"),
        title = R2dbcPersistenceSupport.requiredText(this, "title"),
        content = R2dbcPersistenceSupport.requiredText(this, "content"),
        createdAt = R2dbcPersistenceSupport.instant(this, "created_at") ?: Instant.EPOCH,
        updatedAt = R2dbcPersistenceSupport.instant(this, "updated_at") ?: Instant.EPOCH,
        revision = R2dbcPersistenceSupport.long(this, "revision") ?: 1L,
        readCount = R2dbcPersistenceSupport.long(this, "read_count") ?: 0L,
        lastReadAt = R2dbcPersistenceSupport.instant(this, "last_read_at"),
    )

/** Converts a skill into the bounded search projection. */
internal fun SkillRecord.toSearchRecord(snippet: String): SkillSearchRecord =
    SkillSearchRecord(id, title, snippet.takeCodePoints(320), updatedAt, revision, readCount, lastReadAt)

internal fun String.takeCodePoints(maximum: Int): String =
    if (codePointCount(0, length) <= maximum) this else substring(0, offsetByCodePoints(0, maximum))
