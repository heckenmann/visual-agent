package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.fileSize
import kotlin.io.path.name

/** Builds persisted metadata for files discovered during workspace synchronization. */
internal fun recordForExistingFile(
    path: Path,
    originalName: String = path.name,
    databasePath: String,
    mimeDetector: WorkspaceMimeTypeDetector,
): WorkspaceFileRecord {
    val now = Instant.now()
    return WorkspaceFileRecord(
        id = UUID.randomUUID().toString(),
        relativePath = WorkspaceFilePaths.relativePath(path, databasePath),
        originalName = WorkspaceFilePaths.safeFileName(originalName),
        mimeType = mimeDetector.detect(path),
        sizeBytes = path.fileSize(),
        sha256 = WorkspaceFilePaths.sha256(path),
        extractedText = null,
        importedAt = now,
        updatedAt = now,
    )
}
