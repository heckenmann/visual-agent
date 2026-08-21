package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import de.heckenmann.visualagent.protocol.MAX_WORKSPACE_FILE_IMPORT_BYTES
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.writeBytes
import kotlin.streams.asSequence

/**
 * Manages imported user files in the application workspace next to the SQLite database.
 *
 * Use cases: UC-0000023, UC-0000024, UC-0000025, UC-0000026, UC-0000027, UC-0000031.
 */
@Service
class WorkspaceFileService(
    private val store: WorkspaceFileStore,
    @Qualifier("databasePath")
    private val databasePath: String = "./data/visual-agent.db",
    private val mimeDetector: WorkspaceMimeTypeDetector = WorkspaceMimeTypeDetector(),
    private val activityEvents: WorkspaceFileActivityEventBus? = null,
) {
    private val contentOperations = WorkspaceFileContentOperations(store, mimeDetector, ::resolveManagedPath)

    /**
     * Returns the managed workspace root directory, creating it when necessary.
     *
     * @return Absolute normalized workspace directory
     * @see docs/usecases/uc_0000023_import_workspace_file.md
     */
    fun workspaceRoot(): Path = WorkspaceFilePaths.workspaceRoot(databasePath)

    /** Lists all workspace-relative directories, including empty directories. */
    fun listDirectories(): List<String> = listWorkspaceDirectories(workspaceRoot(), databasePath)

    /** Creates one directory below the supplied workspace-relative parent directory. */
    fun createDirectory(
        parentDirectory: String,
        name: String,
    ): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank() && normalizedName !in setOf(".", "..")) { "Folder name must not be blank" }
        require('/' !in normalizedName && '\\' !in normalizedName) { "Folder name must not contain a path separator" }
        require(WorkspaceFilePaths.safeFileName(normalizedName) == normalizedName) { "Folder name contains unsupported characters" }
        val parent = resolveWorkspaceDirectory(parentDirectory)
        val directory = parent.resolve(normalizedName).normalize()
        require(directory.parent == parent) { "Folder must be a direct child of its parent" }
        directory.createDirectories()
        require(directory.toRealPath().startsWith(workspaceRoot().toRealPath())) { "Workspace directory escapes the workspace" }
        return WorkspaceFilePaths.relativePath(directory, databasePath).also {
            recordActivity("Workspace folder created: $it.")
        }
    }

    private fun resolveWorkspaceDirectory(directoryName: String): Path {
        val normalized = directoryName.replace('\\', '/').trim('/')
        if (normalized.isBlank()) return workspaceRoot()
        val path = Path.of(normalized)
        require(!path.isAbsolute) { "Workspace directory must be relative" }
        require(normalized.split('/').none { it == "." || it == ".." }) { "Workspace directory traversal is not allowed" }
        val root = workspaceRoot().toRealPath()
        val directory = root.resolve(normalized).normalize()
        require(directory.startsWith(root)) { "Workspace directory escapes the workspace" }
        directory.createDirectories()
        require(directory.toRealPath().startsWith(root)) { "Workspace directory escapes the workspace" }
        return directory
    }

    /**
     * Imports one external file into the managed workspace.
     *
     * @param source Existing user-selected file
     * @return Persisted metadata for the imported copy
     * @see docs/usecases/uc_0000023_import_workspace_file.md
     */
    fun importFile(source: File): WorkspaceFileRecord {
        require(source.isFile) { "File does not exist: ${source.name}" }
        require(source.length() <= MAX_WORKSPACE_FILE_IMPORT_BYTES) {
            "File is larger than ${MAX_WORKSPACE_FILE_IMPORT_BYTES / 1024 / 1024} MB"
        }
        val importsDir = workspaceRoot().resolve("imports").also { it.createDirectories() }
        val destination = WorkspaceFilePaths.uniqueDestination(importsDir, source.name)
        Files.copy(source.toPath(), destination)
        return recordManagedFile(destination, source.name).also { recordActivity("Workspace file imported: ${it.relativePath}.") }
    }

    /**
     * Imports file bytes supplied by a presentation or transport adapter.
     *
     * @param originalName User-provided source filename
     * @param bytes Complete source file contents
     * @return Persisted metadata for the imported copy
     */
    fun importFile(
        originalName: String,
        bytes: ByteArray,
    ): WorkspaceFileRecord = importFile("imports", originalName, bytes)

    /** Imports file bytes into a workspace-relative directory. */
    fun importFile(
        directoryName: String,
        originalName: String,
        bytes: ByteArray,
    ): WorkspaceFileRecord {
        require(bytes.size <= MAX_WORKSPACE_FILE_IMPORT_BYTES) {
            "File is larger than ${MAX_WORKSPACE_FILE_IMPORT_BYTES / 1024 / 1024} MB"
        }
        val directory = resolveWorkspaceDirectory(directoryName)
        val destination = WorkspaceFilePaths.uniqueDestination(directory, originalName)
        destination.writeBytes(bytes)
        return recordManagedFile(destination, originalName).also { recordActivity("Workspace file imported: ${it.relativePath}.") }
    }

    /**
     * Creates a managed workspace file from application-owned bytes.
     *
     * @param directoryName Workspace subdirectory such as `canvas` or `generated`
     * @param requestedName Preferred filename
     * @param bytes File bytes to persist
     * @param mimeType Optional MIME type override
     * @return Persisted workspace metadata
     * @see docs/usecases/uc_0000031_save_and_open_canvas_workspace_file.md
     */
    fun createManagedFile(
        directoryName: String,
        requestedName: String,
        bytes: ByteArray,
        mimeType: String? = null,
    ): WorkspaceFileRecord {
        require(bytes.size <= MAX_WORKSPACE_FILE_IMPORT_BYTES) {
            "File is larger than ${MAX_WORKSPACE_FILE_IMPORT_BYTES / 1024 / 1024} MB"
        }
        val directory = workspaceRoot().resolve(WorkspaceFilePaths.safeDirectoryName(directoryName)).also { it.createDirectories() }
        val destination = WorkspaceFilePaths.uniqueDestination(directory, requestedName)
        destination.writeBytes(bytes)
        return recordManagedFile(destination, requestedName, mimeType).also {
            recordActivity("Workspace file created: ${it.relativePath}.")
        }
    }

    internal fun recordManagedFile(
        destination: Path,
        originalName: String,
        mimeType: String? = null,
    ): WorkspaceFileRecord {
        val now = Instant.now()
        val record =
            WorkspaceFileRecord(
                id = UUID.randomUUID().toString(),
                relativePath = WorkspaceFilePaths.relativePath(destination, databasePath),
                originalName = WorkspaceFilePaths.safeFileName(originalName),
                mimeType = mimeType ?: mimeDetector.detect(destination),
                sizeBytes = destination.fileSize(),
                sha256 = WorkspaceFilePaths.sha256(destination),
                extractedText = null,
                importedAt = now,
                updatedAt = now,
            )
        store.saveWorkspaceFile(record)
        return record
    }

    /** Returns all imported workspace files. Use cases: UC-0000024, UC-0000027. */
    fun listFiles(): List<WorkspaceFileRecord> = store.listWorkspaceFiles()

    /**
     * Searches workspace metadata and bounded text/PDF content.
     *
     * @param query Case-insensitive query
     * @return Matching records with compact match descriptions
     * @see docs/usecases/uc_0000025_search_workspace_files.md
     */
    fun searchFiles(query: String): WorkspaceSearchResult {
        val normalized = query.trim().lowercase()
        require(normalized.isNotBlank()) { "Search query must not be blank" }
        val matches =
            listFiles()
                .mapNotNull { record -> searchRecord(record, normalized) }
                .take(MAX_SEARCH_RESULTS)
        return WorkspaceSearchResult(query = query, matches = matches)
    }

    /**
     * Reconciles filesystem content below the managed workspace with persisted metadata.
     *
     * Use cases: UC-0000026.
     */
    fun syncMetadataWithFilesystem(): WorkspaceSyncResult {
        val root = workspaceRoot()
        val existingRecords = listFiles()
        val pathsByRelative = existingRecords.associateBy { WorkspaceFilePaths.normalizeRelativePath(it.relativePath) }
        val filesByRelative =
            Files
                .walk(root)
                .use { stream ->
                    stream
                        .asSequence()
                        .filter { it.isRegularFile() }
                        .associateBy { WorkspaceFilePaths.relativePath(it, databasePath) }
                }
        var added = 0
        var updated = 0
        var removed = 0
        filesByRelative.forEach { (relativePath, path) ->
            val current = pathsByRelative[relativePath]
            if (current == null) {
                store.saveWorkspaceFile(recordForExistingFile(path, path.name, databasePath, mimeDetector))
                added++
            } else {
                val currentHash = WorkspaceFilePaths.sha256(path)
                if (
                    current.sha256 != currentHash ||
                    current.sizeBytes != path.fileSize() ||
                    current.mimeType != mimeDetector.detect(path)
                ) {
                    store.saveWorkspaceFile(
                        current.copy(
                            mimeType = mimeDetector.detect(path),
                            sizeBytes = path.fileSize(),
                            sha256 = currentHash,
                            updatedAt = Instant.now(),
                        ),
                    )
                    updated++
                }
            }
        }
        existingRecords
            .filter { WorkspaceFilePaths.normalizeRelativePath(it.relativePath) !in filesByRelative.keys }
            .forEach {
                if (store.deleteWorkspaceFile(it.id)) removed++
            }
        return WorkspaceSyncResult(added = added, updated = updated, removed = removed, total = listFiles().size).also {
            recordActivity("Workspace files synchronized: added=$added updated=$updated removed=$removed.")
        }
    }

    /**
     * Resolves a file by ID or relative path.
     *
     * @param id Optional workspace file ID
     * @param path Optional workspace-relative path
     * @return Matching file record
     * @see docs/usecases/uc_0000027_analyze_workspace_file_via_tool.md
     */
    fun requireFile(
        id: String?,
        path: String?,
    ): WorkspaceFileRecord =
        id?.let(store::getWorkspaceFile)
            ?: path?.let { store.getWorkspaceFileByPath(WorkspaceFilePaths.normalizeRelativePath(it)) }
            ?: throw de.heckenmann.visualagent.error.WorkspaceFileException(
                summary = "File not found",
                detail = "The requested workspace file was not found. Import the file or check the path.",
                retryable = false,
            )

    /**
     * Deletes a managed file and its metadata.
     *
     * @param id Workspace file ID
     * @return true when a file was deleted
     * @see docs/usecases/uc_0000024_manage_workspace_files.md
     */
    @Transactional
    fun deleteFile(id: String): Boolean {
        val record = store.getWorkspaceFile(id) ?: return false
        val path = WorkspaceFilePaths.resolveWorkspacePath(record.relativePath, databasePath)
        if (path.exists()) {
            require(path.isRegularFile()) { "Workspace path is not a regular file" }
            path.deleteIfExists()
        }
        return store.deleteWorkspaceFile(id).also { deleted ->
            if (deleted) recordActivity("Workspace file deleted: ${record.relativePath}.")
        }
    }

    /** Deletes a workspace directory, optionally including all nested files and directories. */
    @Transactional
    fun deleteDirectory(
        relativePath: String,
        recursive: Boolean = false,
    ): WorkspaceDirectoryDeletion =
        deleteWorkspaceDirectory(
            root = workspaceRoot(),
            requestedPath = relativePath,
            databasePath = databasePath,
            recursive = recursive,
            records = listFiles(),
            deleteMetadata = store::deleteWorkspaceFile,
        ).also { result ->
            recordActivity(
                "Workspace directory deleted: ${result.relativePath} " +
                    "(recursive=${result.recursive}, files=${result.deletedFiles}).",
            )
        }

    /**
     * Renames a managed file and updates persisted metadata.
     *
     * @param id Workspace file ID
     * @param requestedName New filename
     * @return Updated file record
     * @see docs/usecases/uc_0000024_manage_workspace_files.md
     */
    fun renameFile(
        id: String,
        requestedName: String,
    ): WorkspaceFileRecord {
        val current =
            store.getWorkspaceFile(id)
                ?: throw de.heckenmann.visualagent.error.WorkspaceFileException(
                    summary = "File not found",
                    detail = "The workspace file to rename was not found. Refresh the file list and try again.",
                    retryable = true,
                )
        val source = resolveManagedPath(current.relativePath)
        val safeName = WorkspaceFilePaths.safeFileName(requestedName)
        val targetName = WorkspaceFilePaths.preserveExtensionIfMissing(source, safeName)
        val destination = WorkspaceFilePaths.uniqueDestination(source.parent, targetName)
        Files.move(source, destination)
        val updated =
            current.copy(
                relativePath = WorkspaceFilePaths.relativePath(destination, databasePath),
                mimeType = mimeDetector.detect(destination),
                sizeBytes = destination.fileSize(),
                sha256 = WorkspaceFilePaths.sha256(destination),
                updatedAt = Instant.now(),
            )
        store.saveWorkspaceFile(updated)
        return updated.also { recordActivity("Workspace file renamed: ${current.relativePath} to ${it.relativePath}.") }
    }

    /**
     * Computes the current SHA-256 hash for a managed file.
     *
     * Use cases: UC-0000027.
     */
    fun hash(record: WorkspaceFileRecord): String = contentOperations.hash(record)

    /**
     * Reads bounded UTF-8 text from a managed file.
     *
     * Use cases: UC-0000027.
     */
    fun readText(record: WorkspaceFileRecord): String = contentOperations.readText(record)

    /**
     * Extracts and caches text from a managed PDF.
     *
     * Use cases: UC-0000027.
     */
    fun extractPdfText(record: WorkspaceFileRecord): WorkspaceFileText = contentOperations.extractPdfText(record)

    /**
     * Renders one PDF page into a toolkit-neutral PNG preview and records it as a generated workspace file.
     *
     * Use cases: UC-0000027.
     */
    fun renderPdfPage(
        record: WorkspaceFileRecord,
        page: Int,
    ): WorkspaceFileRecord =
        contentOperations.renderPdfPage(
            requireFile(record.id, null),
            page,
            ::createManagedFile,
        )

    /**
     * Reads image dimensions and metadata.
     */
    fun imageInfo(record: WorkspaceFileRecord): WorkspaceImageInfo = contentOperations.imageInfo(record)

    /**
     * Returns bounded base64 bytes for image/tool transport.
     */
    fun imageBytes(record: WorkspaceFileRecord): WorkspaceImageBytes = contentOperations.imageBytes(record)

    /** Detects a managed file MIME type from bounded content bytes and returns its metadata. */
    fun detectMimeType(record: WorkspaceFileRecord): WorkspaceMimeTypeInfo = contentOperations.detectMimeType(record)

    /**
     * Resolves a workspace-relative path and guarantees it stays inside the managed workspace.
     */
    fun resolveManagedPath(relativePath: String): Path = WorkspaceFilePaths.resolveManagedPath(relativePath, databasePath)

    private fun recordActivity(message: String) {
        activityEvents?.publish(WorkspaceFileActivity(message))
    }

    private companion object {
        const val MAX_SEARCH_RESULTS = 50
    }
}

internal fun String.snippet(index: Int): String {
    val start = (index - 80).coerceAtLeast(0)
    val end = (index + 160).coerceAtMost(length)
    return substring(start, end).replace(Regex("\\s+"), " ").trim()
}
