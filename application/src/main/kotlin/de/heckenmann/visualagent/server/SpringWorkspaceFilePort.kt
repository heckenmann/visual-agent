package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.protocol.WorkspaceFile
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import de.heckenmann.visualagent.protocol.WorkspaceSyncResult
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import org.springframework.stereotype.Component

/** Maps managed workspace files to the neutral [WorkspaceFilePort]. */
@Component
class SpringWorkspaceFilePort(
    private val workspaceFileService: WorkspaceFileService,
) : WorkspaceFilePort {
    override fun workspaceRoot(): String = protocolBoundary { workspaceFileService.workspaceRoot().toString() }

    override fun listFiles(): List<WorkspaceFile> =
        protocolBoundary { workspaceFileService.listFiles().map(WorkspaceFileRecord::toProtocol) }

    override fun importFile(
        name: String,
        bytes: ByteArray,
    ): WorkspaceFile = protocolBoundary { workspaceFileService.importFile(name, bytes).toProtocol() }

    override fun createManagedFile(
        directoryName: String,
        requestedName: String,
        bytes: ByteArray,
        mimeType: String?,
    ): WorkspaceFile =
        protocolBoundary { workspaceFileService.createManagedFile(directoryName, requestedName, bytes, mimeType).toProtocol() }

    override fun syncMetadataWithFilesystem(): WorkspaceSyncResult =
        protocolBoundary {
            workspaceFileService.syncMetadataWithFilesystem().let { result ->
                WorkspaceSyncResult(result.added, result.updated, result.removed, result.total)
            }
        }

    override fun renameFile(
        id: String,
        requestedName: String,
    ): WorkspaceFile = protocolBoundary { workspaceFileService.renameFile(id, requestedName).toProtocol() }

    override fun deleteFile(id: String): Boolean = protocolBoundary { workspaceFileService.deleteFile(id) }

    override fun readBytes(relativePath: String): ByteArray =
        protocolBoundary {
            workspaceFileService.resolveManagedPath(relativePath).toFile().readBytes()
        }

    override fun addListener(listener: () -> Unit): AutoCloseable = AutoCloseable {}
}

private fun WorkspaceFileRecord.toProtocol(): WorkspaceFile =
    WorkspaceFile(
        id = id,
        relativePath = relativePath,
        originalName = originalName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        importedAt = importedAt.toString(),
        updatedAt = updatedAt.toString(),
    )
