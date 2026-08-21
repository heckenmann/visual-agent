package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.protocol.WorkspaceDownload
import de.heckenmann.visualagent.protocol.WorkspaceFile
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import de.heckenmann.visualagent.protocol.WorkspaceSyncResult
import de.heckenmann.visualagent.workspace.WorkspaceDownloadService
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import org.springframework.stereotype.Component

/** Maps managed workspace files to the neutral [WorkspaceFilePort]. */
@Component
class SpringWorkspaceFilePort(
    private val workspaceFileService: WorkspaceFileService,
    private val workspaceDownloadService: WorkspaceDownloadService,
) : WorkspaceFilePort {
    override fun workspaceRoot(): String = protocolBoundary { workspaceFileService.workspaceRoot().toString() }

    override fun listFiles(): List<WorkspaceFile> =
        protocolBoundary { workspaceFileService.listFiles().map(WorkspaceFileRecord::toProtocol) }

    override fun listDirectories(): List<String> = protocolBoundary { workspaceFileService.listDirectories() }

    override fun createDirectory(
        parentDirectory: String,
        name: String,
    ): String = protocolBoundary { workspaceFileService.createDirectory(parentDirectory, name) }

    override fun importFile(
        directory: String,
        name: String,
        bytes: ByteArray,
    ): WorkspaceFile = protocolBoundary { workspaceFileService.importFile(directory, name, bytes).toProtocol() }

    override fun activeDownloads(): List<WorkspaceDownload> = protocolBoundary { workspaceDownloadService.activeDownloads() }

    override fun pauseDownload(id: String) = protocolBoundary { workspaceDownloadService.pauseDownload(id) }

    override fun resumeDownload(id: String) = protocolBoundary { workspaceDownloadService.resumeDownload(id) }

    override fun cancelDownload(id: String) = protocolBoundary { workspaceDownloadService.cancelDownload(id) }

    override fun addDownloadListener(listener: () -> Unit): AutoCloseable = workspaceDownloadService.addListener(listener)

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
