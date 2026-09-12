package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.error.WorkspaceFileException
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.fileSize

/** Renames a managed workspace file and updates the matching metadata record. */
internal class WorkspaceFileRenameOperations(
    private val store: WorkspaceFileStore,
    private val resolvePath: (String) -> Path,
    private val databasePath: String,
    private val mimeDetector: WorkspaceMimeTypeDetector,
    private val recordActivity: (String, String?, String?, String?, Long?) -> Unit,
) {
    fun rename(
        id: String,
        requestedName: String,
    ): WorkspaceFileRecord {
        val current =
            store.getWorkspaceFile(id)
                ?: throw WorkspaceFileException(
                    "File not found",
                    "The workspace file to rename was not found. Refresh the file list and try again.",
                    retryable = true,
                )
        val source = resolvePath(current.relativePath)
        val destination =
            WorkspaceFilePaths.uniqueDestination(
                source.parent,
                WorkspaceFilePaths.preserveExtensionIfMissing(source, WorkspaceFilePaths.safeFileName(requestedName)),
            )
        Files.move(source, destination)
        return current
            .copy(
                relativePath = WorkspaceFilePaths.relativePath(destination, databasePath),
                mimeType = mimeDetector.detect(destination),
                sizeBytes = destination.fileSize(),
                sha256 = WorkspaceFilePaths.sha256(destination),
                updatedAt = Instant.now(),
            ).also { updated ->
                store.saveWorkspaceFile(updated)
                recordActivity(
                    "Workspace file renamed: ${current.relativePath} to ${updated.relativePath}.",
                    updated.relativePath,
                    "rename",
                    updated.mimeType,
                    updated.sizeBytes,
                )
            }
    }
}
