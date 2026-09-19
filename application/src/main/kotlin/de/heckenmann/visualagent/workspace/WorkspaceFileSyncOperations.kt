package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

/** Synchronizes managed workspace files with persisted metadata. */
internal class WorkspaceFileSyncOperations(
    private val store: WorkspaceFileStore,
    private val workspaceRoot: () -> Path,
    private val mimeDetector: WorkspaceMimeTypeDetector,
    private val recordActivity: (String, String?, String?, String?, Long?) -> Unit,
) {
    fun syncMetadataWithFilesystem(): WorkspaceSyncResult {
        val root = workspaceRoot()
        val existingRecords = store.listWorkspaceFiles()
        val pathsByRelative = existingRecords.associateBy { WorkspaceFilePaths.normalizeRelativePath(it.relativePath) }
        val filesByRelative = discoverFiles(root)
        var added = 0
        var updated = 0
        filesByRelative.forEach { (relativePath, path) ->
            val current = pathsByRelative[relativePath]
            if (current == null) {
                store.saveWorkspaceFile(recordForExistingFile(path, path.name, root, mimeDetector))
                added++
            } else if (hasChanged(current, path)) {
                store.saveWorkspaceFile(updatedRecord(current, path))
                updated++
            }
        }
        val removed =
            existingRecords
                .filter { WorkspaceFilePaths.normalizeRelativePath(it.relativePath) !in filesByRelative.keys }
                .count { store.deleteWorkspaceFile(it.id) }
        return WorkspaceSyncResult(added = added, updated = updated, removed = removed, total = store.listWorkspaceFiles().size).also {
            recordActivity("Workspace files synchronized: added=$added updated=$updated removed=$removed.", null, "sync", null, null)
        }
    }

    private fun discoverFiles(root: Path): Map<String, Path> =
        Files.walk(root).use { stream ->
            stream.asSequence().filter { it.isRegularFile() }.associateBy { WorkspaceFilePaths.relativePath(it, root) }
        }

    private fun hasChanged(
        record: WorkspaceFileRecord,
        path: Path,
    ): Boolean =
        record.sha256 != WorkspaceFilePaths.sha256(path) ||
            record.sizeBytes != path.fileSize() ||
            record.mimeType != mimeDetector.detect(path)

    private fun updatedRecord(
        record: WorkspaceFileRecord,
        path: Path,
    ): WorkspaceFileRecord =
        record.copy(
            mimeType = mimeDetector.detect(path),
            sizeBytes = path.fileSize(),
            sha256 = WorkspaceFilePaths.sha256(path),
            updatedAt = Instant.now(),
        )
}
