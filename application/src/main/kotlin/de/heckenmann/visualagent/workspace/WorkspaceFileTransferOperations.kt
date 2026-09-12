package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.fileSize
import kotlin.io.path.name

/** Performs metadata-preserving file copies and moves within the managed workspace. */
internal class WorkspaceFileTransferOperations(
    private val findByPath: (String) -> WorkspaceFileRecord,
    private val resolvePath: (String) -> Path,
    private val prepareTarget: (String) -> Path,
    private val persist: (WorkspaceFileRecord) -> Unit,
    private val databasePath: String,
    private val mimeDetector: WorkspaceMimeTypeDetector,
    private val recordActivity: (String, String?, String?, String?, Long?) -> Unit,
) {
    fun copy(
        sourcePath: String,
        targetPath: String,
    ): WorkspaceFileRecord {
        val sourceRecord = findByPath(sourcePath)
        val target = prepareTarget(targetPath)
        Files.copy(resolvePath(sourceRecord.relativePath), target)
        return updateRecord(sourceRecord.copy(id = UUID.randomUUID().toString(), importedAt = Instant.now()), target).also { copied ->
            persist(copied)
            recordActivity(
                "Workspace file copied: ${sourceRecord.relativePath} to ${copied.relativePath}.",
                copied.relativePath,
                "copy",
                copied.mimeType,
                copied.sizeBytes,
            )
        }
    }

    fun move(
        sourcePath: String,
        targetPath: String,
    ): WorkspaceFileRecord {
        val sourceRecord = findByPath(sourcePath)
        val target = prepareTarget(targetPath)
        try {
            Files.move(resolvePath(sourceRecord.relativePath), target, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(resolvePath(sourceRecord.relativePath), target)
        }
        return updateRecord(sourceRecord, target).also { moved ->
            persist(moved)
            recordActivity(
                "Workspace file moved: ${sourceRecord.relativePath} to ${moved.relativePath}.",
                moved.relativePath,
                "move",
                moved.mimeType,
                moved.sizeBytes,
            )
        }
    }

    private fun updateRecord(
        source: WorkspaceFileRecord,
        target: Path,
    ): WorkspaceFileRecord =
        source.copy(
            relativePath = WorkspaceFilePaths.relativePath(target, databasePath),
            originalName = target.name,
            mimeType = mimeDetector.detect(target),
            sizeBytes = target.fileSize(),
            sha256 = WorkspaceFilePaths.sha256(target),
            updatedAt = Instant.now(),
        )
}
