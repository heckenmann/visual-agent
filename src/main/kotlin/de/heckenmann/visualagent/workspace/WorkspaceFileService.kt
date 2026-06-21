package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import javafx.scene.image.Image
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
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
) {
    /**
     * Returns the managed workspace root directory, creating it when necessary.
     *
     * @return Absolute normalized workspace directory
     * @see docs/usecases/uc_0000023_import_workspace_file.md
     */
    fun workspaceRoot(): Path = WorkspaceFilePaths.workspaceRoot()

    /**
     * Imports one external file into the managed workspace.
     *
     * @param source Existing user-selected file
     * @return Persisted metadata for the imported copy
     * @see docs/usecases/uc_0000023_import_workspace_file.md
     */
    fun importFile(source: File): WorkspaceFileRecord {
        require(source.isFile) { "File does not exist: ${source.name}" }
        require(source.length() <= MAX_IMPORT_BYTES) { "File is larger than ${MAX_IMPORT_BYTES / 1024 / 1024} MB" }
        val importsDir = workspaceRoot().resolve("imports").also { it.createDirectories() }
        val destination = WorkspaceFilePaths.uniqueDestination(importsDir, source.name)
        Files.copy(source.toPath(), destination)
        return recordManagedFile(destination, source.name)
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
        require(bytes.size <= MAX_IMPORT_BYTES) { "File is larger than ${MAX_IMPORT_BYTES / 1024 / 1024} MB" }
        val directory = workspaceRoot().resolve(WorkspaceFilePaths.safeDirectoryName(directoryName)).also { it.createDirectories() }
        val destination = WorkspaceFilePaths.uniqueDestination(directory, requestedName)
        destination.writeBytes(bytes)
        return recordManagedFile(destination, requestedName, mimeType)
    }

    private fun recordManagedFile(
        destination: Path,
        originalName: String,
        mimeType: String? = null,
    ): WorkspaceFileRecord {
        val now = Instant.now()
        val record =
            WorkspaceFileRecord(
                id = UUID.randomUUID().toString(),
                relativePath = WorkspaceFilePaths.relativePath(destination),
                originalName = WorkspaceFilePaths.safeFileName(originalName),
                mimeType = mimeType ?: WorkspaceFilePaths.detectMimeType(destination),
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
                        .associateBy { WorkspaceFilePaths.relativePath(it) }
                }
        var added = 0
        var updated = 0
        var removed = 0
        filesByRelative.forEach { (relativePath, path) ->
            val current = pathsByRelative[relativePath]
            if (current == null) {
                store.saveWorkspaceFile(recordForExistingFile(path, path.name))
                added++
            } else {
                val currentHash = WorkspaceFilePaths.sha256(path)
                if (
                    current.sha256 != currentHash ||
                    current.sizeBytes != path.fileSize() ||
                    current.mimeType != WorkspaceFilePaths.detectMimeType(path)
                ) {
                    store.saveWorkspaceFile(
                        current.copy(
                            mimeType = WorkspaceFilePaths.detectMimeType(path),
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
        return WorkspaceSyncResult(added = added, updated = updated, removed = removed, total = listFiles().size)
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
            ?: throw IllegalArgumentException("Workspace file not found")

    /**
     * Deletes a managed file and its metadata.
     *
     * @param id Workspace file ID
     * @return true when a file was deleted
     * @see docs/usecases/uc_0000024_manage_workspace_files.md
     */
    fun deleteFile(id: String): Boolean {
        val record = store.getWorkspaceFile(id) ?: return false
        resolveManagedPath(record.relativePath).deleteIfExists()
        return store.deleteWorkspaceFile(id)
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
        val current = store.getWorkspaceFile(id) ?: throw IllegalArgumentException("Workspace file not found")
        val source = resolveManagedPath(current.relativePath)
        val safeName = WorkspaceFilePaths.safeFileName(requestedName)
        val targetName = WorkspaceFilePaths.preserveExtensionIfMissing(source, safeName)
        val destination = WorkspaceFilePaths.uniqueDestination(source.parent, targetName)
        Files.move(source, destination)
        val updated =
            current.copy(
                relativePath = WorkspaceFilePaths.relativePath(destination),
                mimeType = WorkspaceFilePaths.detectMimeType(destination),
                sizeBytes = destination.fileSize(),
                sha256 = WorkspaceFilePaths.sha256(destination),
                updatedAt = Instant.now(),
            )
        store.saveWorkspaceFile(updated)
        return updated
    }

    /**
     * Computes the current SHA-256 hash for a managed file.
     *
     * Use cases: UC-0000027.
     */
    fun hash(record: WorkspaceFileRecord): String = WorkspaceFilePaths.sha256(resolveManagedPath(record.relativePath))

    /**
     * Reads bounded UTF-8 text from a managed file.
     *
     * Use cases: UC-0000027.
     */
    fun readText(record: WorkspaceFileRecord): String =
        resolveManagedPath(record.relativePath).readText(Charsets.UTF_8).take(MAX_TEXT_CHARS)

    /**
     * Extracts and caches text from a managed PDF.
     *
     * Use cases: UC-0000027.
     */
    fun extractPdfText(record: WorkspaceFileRecord): WorkspaceFileText {
        record.extractedText?.let { return WorkspaceFileText(it.take(MAX_TEXT_CHARS), cached = true) }
        val path = resolveManagedPath(record.relativePath)
        val text =
            Loader.loadPDF(path.toFile()).use { document ->
                PDFTextStripper().getText(document).trim().take(MAX_TEXT_CHARS)
            }
        store.saveWorkspaceFile(record.copy(extractedText = text, updatedAt = Instant.now()))
        return WorkspaceFileText(text, cached = false)
    }

    /**
     * Reports that PDF page rendering needs a non-desktop renderer integration.
     */
    fun renderPdfPage(
        record: WorkspaceFileRecord,
        page: Int,
    ): WorkspaceFileRecord {
        require(page >= 1) { "PDF page must be >= 1" }
        requireFile(record.id, null)
        throw UnsupportedOperationException("PDF page rendering is unavailable without a non-desktop renderer")
    }

    /**
     * Reads image dimensions and metadata.
     */
    fun imageInfo(record: WorkspaceFileRecord): WorkspaceImageInfo {
        val path = resolveManagedPath(record.relativePath)
        val image = Files.newInputStream(path).use(::Image)
        require(!image.isError && image.width > 0.0 && image.height > 0.0) { "Unsupported image file" }
        return WorkspaceImageInfo(
            width = image.width.toInt(),
            height = image.height.toInt(),
            mimeType = WorkspaceFilePaths.detectMimeType(path),
            sizeBytes = path.fileSize(),
            sha256 = WorkspaceFilePaths.sha256(path),
        )
    }

    /**
     * Returns bounded base64 bytes for image/tool transport.
     */
    fun imageBytes(record: WorkspaceFileRecord): WorkspaceImageBytes {
        val path = resolveManagedPath(record.relativePath)
        require(path.fileSize() <= MAX_BASE64_BYTES) { "Image is larger than ${MAX_BASE64_BYTES / 1024 / 1024} MB" }
        return WorkspaceImageBytes(
            mimeType = WorkspaceFilePaths.detectMimeType(path),
            base64 = Base64.getEncoder().encodeToString(path.readBytes()),
        )
    }

    /**
     * Resolves a workspace-relative path and guarantees it stays inside the managed workspace.
     */
    fun resolveManagedPath(relativePath: String): Path = WorkspaceFilePaths.resolveManagedPath(relativePath)

    private fun searchRecord(
        record: WorkspaceFileRecord,
        query: String,
    ): WorkspaceSearchMatch? {
        val metadataHaystack =
            listOf(record.relativePath, record.originalName, record.mimeType, record.sha256)
                .joinToString("\n")
                .lowercase()
        if (metadataHaystack.contains(query)) {
            return WorkspaceSearchMatch(record, "metadata", record.relativePath)
        }
        val path = runCatching { resolveManagedPath(record.relativePath) }.getOrNull() ?: return null
        val text =
            when {
                record.mimeType == "application/pdf" -> runCatching { extractPdfText(record).text }.getOrNull()
                record.mimeType.startsWith("text/") || path.extension.lowercase() in WorkspaceFilePaths.TEXT_EXTENSIONS ->
                    runCatching { path.readText(Charsets.UTF_8).take(MAX_TEXT_CHARS) }.getOrNull()
                else -> null
            } ?: return null
        val index = text.lowercase().indexOf(query)
        if (index < 0) return null
        return WorkspaceSearchMatch(record, "content", text.snippet(index))
    }

    private fun recordForExistingFile(
        path: Path,
        originalName: String,
    ): WorkspaceFileRecord {
        val now = Instant.now()
        return WorkspaceFileRecord(
            id = UUID.randomUUID().toString(),
            relativePath = WorkspaceFilePaths.relativePath(path),
            originalName = WorkspaceFilePaths.safeFileName(originalName),
            mimeType = WorkspaceFilePaths.detectMimeType(path),
            sizeBytes = path.fileSize(),
            sha256 = WorkspaceFilePaths.sha256(path),
            extractedText = null,
            importedAt = now,
            updatedAt = now,
        )
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 50L * 1024L * 1024L
        const val MAX_BASE64_BYTES = 8L * 1024L * 1024L
        const val MAX_TEXT_CHARS = 120_000
        const val MAX_SEARCH_RESULTS = 50
    }
}

private fun String.snippet(index: Int): String {
    val start = (index - 80).coerceAtLeast(0)
    val end = (index + 160).coerceAtMost(length)
    return substring(start, end).replace(Regex("\\s+"), " ").trim()
}
