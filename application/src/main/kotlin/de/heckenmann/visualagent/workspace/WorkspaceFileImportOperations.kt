package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.protocol.MAX_WORKSPACE_FILE_IMPORT_BYTES
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/** Keeps workspace import and application-owned file creation separate from the service facade. */
internal class WorkspaceFileImportOperations(
    private val workspaceRoot: () -> Path,
    private val resolveDirectory: (String) -> Path,
    private val recordFile: (Path, String, String?) -> WorkspaceFileRecord,
    private val recordActivity: (String, String?, String?, String?, Long?) -> Unit,
) {
    fun importFile(source: File): WorkspaceFileRecord {
        require(source.isFile) { "File does not exist: ${source.name}" }
        requireSize(source.length())
        val destination =
            WorkspaceFilePaths.uniqueDestination(
                workspaceRoot().resolve("imports").also { it.createDirectories() },
                source.name,
            )
        Files.copy(source.toPath(), destination)
        return recordImported(destination, source.name)
    }

    fun importFile(
        originalName: String,
        bytes: ByteArray,
    ): WorkspaceFileRecord = importFile("imports", originalName, bytes)

    fun importFile(
        directoryName: String,
        originalName: String,
        bytes: ByteArray,
    ): WorkspaceFileRecord {
        requireSize(bytes.size.toLong())
        val destination = WorkspaceFilePaths.uniqueDestination(resolveDirectory(directoryName), originalName)
        destination.writeBytes(bytes)
        return recordImported(destination, originalName)
    }

    fun createManagedFile(
        directoryName: String,
        requestedName: String,
        bytes: ByteArray,
        mimeType: String?,
    ): WorkspaceFileRecord {
        requireSize(bytes.size.toLong())
        val directory = workspaceRoot().resolve(WorkspaceFilePaths.safeDirectoryName(directoryName)).also { it.createDirectories() }
        val destination = WorkspaceFilePaths.uniqueDestination(directory, requestedName)
        destination.writeBytes(bytes)
        return recordFile(destination, requestedName, mimeType).also {
            recordActivity("Workspace file created: ${it.relativePath}.", it.relativePath, "create", it.mimeType, it.sizeBytes)
        }
    }

    private fun recordImported(
        destination: Path,
        originalName: String,
    ): WorkspaceFileRecord =
        recordFile(destination, originalName, null).also {
            recordActivity("Workspace file imported: ${it.relativePath}.", it.relativePath, "import", it.mimeType, it.sizeBytes)
        }

    private fun requireSize(size: Long) {
        require(size <= MAX_WORKSPACE_FILE_IMPORT_BYTES) {
            "File is larger than ${MAX_WORKSPACE_FILE_IMPORT_BYTES / 1024 / 1024} MB"
        }
    }
}
