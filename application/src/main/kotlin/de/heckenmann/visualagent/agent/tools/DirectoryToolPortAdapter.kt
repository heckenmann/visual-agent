package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.DirectoryToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryEntry
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryGrant
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryMatch
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryMimeType
import de.heckenmann.visualagent.protocol.FileReference
import de.heckenmann.visualagent.workspace.DirectoryGrantService
import de.heckenmann.visualagent.workspace.UnifiedFileService
import de.heckenmann.visualagent.workspace.WorkspaceMimeTypeDetector
import org.springframework.stereotype.Component

/** Server-owned adapter between the model tool and directory authorization service. */
@Component
class DirectoryToolPortAdapter(
    private val grants: DirectoryGrantService,
    private val files: UnifiedFileService,
    private val mimeDetector: WorkspaceMimeTypeDetector,
) : DirectoryToolPort {
    override fun listGrants(): List<ToolDirectoryGrant> =
        grants.listGrants().map { ToolDirectoryGrant(it.id, it.displayName, it.origin.name, it.mode.name, grants.isAvailable(it)) }

    override fun list(
        grantId: String,
        path: String,
    ): List<ToolDirectoryEntry> = files.list(FileReference(grantId, path)).map { ToolDirectoryEntry(it.path, it.directory, it.sizeBytes) }

    override fun readText(
        grantId: String,
        path: String,
    ): String = files.readText(FileReference(grantId, path))

    override fun detectMimeType(
        grantId: String,
        path: String,
    ): ToolDirectoryMimeType =
        files.readBytes(FileReference(grantId, path), MAX_MIME_DETECTION_BYTES).let { bytes ->
            ToolDirectoryMimeType(mimeDetector.detect(bytes), bytes.size)
        }

    override fun search(
        grantId: String,
        query: String,
        path: String,
    ): List<ToolDirectoryMatch> = grants.search(grantId, query, path).map { ToolDirectoryMatch(it.path, it.line, it.snippet) }

    override fun glob(
        grantId: String,
        path: String,
        pattern: String,
    ): List<ToolDirectoryEntry> = grants.glob(grantId, path, pattern).map { ToolDirectoryEntry(it.path, it.directory, it.sizeBytes) }

    override fun writeText(
        grantId: String,
        path: String,
        content: String,
    ): String = files.writeText(FileReference(grantId, path), content)

    override fun editText(
        grantId: String,
        path: String,
        oldText: String,
        newText: String,
    ): String = grants.editText(grantId, path, oldText, newText)

    override fun createDirectory(
        grantId: String,
        path: String,
    ): String = grants.createDirectory(grantId, path)

    override fun delete(
        grantId: String,
        path: String,
        recursive: Boolean,
    ) = files.delete(FileReference(grantId, path), recursive)

    override fun copy(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ): String = grants.copy(sourceGrantId, sourcePath, targetGrantId, targetPath)

    override fun move(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ): String = grants.move(sourceGrantId, sourcePath, targetGrantId, targetPath)

    private companion object {
        const val MAX_MIME_DETECTION_BYTES = 1024 * 1024L
    }
}
