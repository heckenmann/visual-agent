package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.streams.asSequence

/** Lists workspace-relative directories independently from file metadata operations. */
internal fun listWorkspaceDirectories(
    root: Path,
    databasePath: String,
): List<String> =
    Files
        .walk(root)
        .use { paths ->
            paths
                .asSequence()
                .filter { it != root && Files.isDirectory(it) }
                .map { WorkspaceFilePaths.relativePath(it, databasePath) }
                .sorted()
                .toList()
        }

/** Result of deleting one managed workspace directory. */
data class WorkspaceDirectoryDeletion(
    val relativePath: String,
    val recursive: Boolean,
    val deletedFiles: Int,
    val deletedMetadata: Int,
)

/** Deletes a workspace directory and, when requested, all of its descendants. */
internal fun deleteWorkspaceDirectory(
    root: Path,
    requestedPath: String,
    databasePath: String,
    recursive: Boolean,
    records: List<WorkspaceFileRecord>,
    deleteMetadata: (String) -> Boolean,
): WorkspaceDirectoryDeletion {
    val relativePath = WorkspaceFilePaths.normalizeRelativePath(requestedPath).trim('/')
    require(relativePath.isNotBlank()) { "The workspace root cannot be deleted" }
    val directory = WorkspaceFilePaths.resolveWorkspacePath(relativePath, databasePath)
    require(directory.startsWith(root.toAbsolutePath().normalize())) { "Workspace directory escapes the workspace" }
    require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        "Workspace directory does not exist: $relativePath"
    }
    val children = Files.list(directory).use { stream -> stream.asSequence().toList() }
    require(recursive || children.isEmpty()) {
        "Workspace directory is not empty; set recursive=true to delete its contents"
    }
    val matchingRecords =
        records.filter { record ->
            val recordPath = WorkspaceFilePaths.normalizeRelativePath(record.relativePath)
            recordPath.startsWith("$relativePath/")
        }
    val pathsToDelete =
        if (recursive) {
            Files.walk(directory).use { stream ->
                stream.asSequence().sortedByDescending { it.nameCount }.toList()
            }
        } else {
            listOf(directory)
        }
    var deletedFiles = 0
    pathsToDelete.forEach { path ->
        if (path != directory && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) deletedFiles++
        Files.deleteIfExists(path)
    }
    val deletedMetadata = matchingRecords.count { deleteMetadata(it.id) }
    return WorkspaceDirectoryDeletion(relativePath, recursive, deletedFiles, deletedMetadata)
}
