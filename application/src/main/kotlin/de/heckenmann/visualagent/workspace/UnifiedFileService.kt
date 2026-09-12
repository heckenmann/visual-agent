package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.protocol.FileReference
import org.springframework.stereotype.Service

/**
 * Routes opaque file references to their filesystem-owning backend.
 *
 * The managed workspace and persisted server grants are resolved locally. Client grants are
 * forwarded by [DirectoryGrantService] only to the exact live client capability that owns them.
 */
@Service
class UnifiedFileService(
    private val workspaceFiles: WorkspaceFileService,
    private val directoryGrants: DirectoryGrantService,
) {
    /** Lists entries below an authorized root-relative directory. */
    fun list(reference: FileReference): List<GrantedDirectoryEntry> =
        if (reference.rootId == WORKSPACE_ROOT_ID) {
            workspaceFiles
                .listDirectories()
                .filter { directory -> directory.substringBeforeLast('/', "") == reference.relativePath.trim('/') }
                .map { GrantedDirectoryEntry(it, directory = true, sizeBytes = null) } +
                workspaceFiles
                    .listFiles()
                    .filter { file -> file.relativePath.substringBeforeLast('/', "") == reference.relativePath.trim('/') }
                    .map { file -> GrantedDirectoryEntry(file.relativePath, directory = false, sizeBytes = file.sizeBytes) }
        } else {
            directoryGrants.list(reference.rootId, reference.relativePath)
        }

    /** Reads bounded text through the filesystem owner's authorization boundary. */
    fun readText(reference: FileReference): String =
        if (reference.rootId == WORKSPACE_ROOT_ID) {
            workspaceFiles.readText(workspaceFiles.requireFile(null, reference.relativePath))
        } else {
            directoryGrants.readText(reference.rootId, reference.relativePath)
        }

    /** Reads bounded bytes through the filesystem owner's authorization boundary. */
    fun readBytes(
        reference: FileReference,
        maximumBytes: Long,
    ): ByteArray =
        if (reference.rootId == WORKSPACE_ROOT_ID) {
            val record = workspaceFiles.requireFile(null, reference.relativePath)
            workspaceFiles.readBytes(record, maximumBytes)
        } else {
            directoryGrants.readBytes(reference.rootId, reference.relativePath, maximumBytes)
        }

    /** Writes text through the filesystem owner's authorization boundary. */
    fun writeText(
        reference: FileReference,
        content: String,
    ): String =
        if (reference.rootId == WORKSPACE_ROOT_ID) {
            workspaceFiles.writeText(reference.relativePath, content).relativePath
        } else {
            directoryGrants.writeText(reference.rootId, reference.relativePath, content)
        }

    /** Deletes a file or directory through the filesystem owner's authorization boundary. */
    fun delete(
        reference: FileReference,
        recursive: Boolean,
    ) {
        if (reference.rootId == WORKSPACE_ROOT_ID) {
            if (reference.relativePath in workspaceFiles.listDirectories()) {
                workspaceFiles.deleteDirectory(reference.relativePath, recursive)
            } else {
                workspaceFiles.deleteFile(workspaceFiles.requireFile(null, reference.relativePath).id)
            }
        } else {
            directoryGrants.delete(reference.rootId, reference.relativePath, recursive)
        }
    }

    private companion object {
        const val WORKSPACE_ROOT_ID = "workspace"
    }
}
