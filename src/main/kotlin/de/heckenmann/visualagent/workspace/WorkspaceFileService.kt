package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import javafx.scene.image.Image
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
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
 */
@Service
class WorkspaceFileService(
    private val store: WorkspaceFileStore,
) {
    /**
     * Returns the managed workspace root directory, creating it when necessary.
     *
     * @return Absolute normalized workspace directory
     */
    fun workspaceRoot(): Path {
        val dbPath = normalizedDatabasePath()
        val parent = dbPath.parent ?: Path.of("data").toAbsolutePath().normalize()
        return parent
            .resolve("workspace")
            .toAbsolutePath()
            .normalize()
            .also { it.createDirectories() }
    }

    /**
     * Imports one external file into the managed workspace.
     *
     * @param source Existing user-selected file
     * @return Persisted metadata for the imported copy
     */
    fun importFile(source: File): WorkspaceFileRecord {
        require(source.isFile) { "File does not exist: ${source.name}" }
        require(source.length() <= MAX_IMPORT_BYTES) { "File is larger than ${MAX_IMPORT_BYTES / 1024 / 1024} MB" }
        val importsDir = workspaceRoot().resolve("imports").also { it.createDirectories() }
        val destination = uniqueDestination(importsDir, source.name)
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
     */
    fun createManagedFile(
        directoryName: String,
        requestedName: String,
        bytes: ByteArray,
        mimeType: String? = null,
    ): WorkspaceFileRecord {
        require(bytes.size <= MAX_IMPORT_BYTES) { "File is larger than ${MAX_IMPORT_BYTES / 1024 / 1024} MB" }
        val directory = workspaceRoot().resolve(safeDirectoryName(directoryName)).also { it.createDirectories() }
        val destination = uniqueDestination(directory, requestedName)
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
                relativePath = relativePath(destination),
                originalName = safeFileName(originalName),
                mimeType = mimeType ?: detectMimeType(destination),
                sizeBytes = destination.fileSize(),
                sha256 = sha256(destination),
                extractedText = null,
                importedAt = now,
                updatedAt = now,
            )
        store.saveWorkspaceFile(record)
        return record
    }

    /** Returns all imported workspace files. */
    fun listFiles(): List<WorkspaceFileRecord> = store.listWorkspaceFiles()

    /**
     * Searches workspace metadata and bounded text/PDF content.
     *
     * @param query Case-insensitive query
     * @return Matching records with compact match descriptions
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
     */
    fun syncMetadataWithFilesystem(): WorkspaceSyncResult {
        val root = workspaceRoot()
        val existingRecords = listFiles()
        val pathsByRelative = existingRecords.associateBy { normalizeRelativePath(it.relativePath) }
        val filesByRelative =
            Files
                .walk(root)
                .use { stream ->
                    stream
                        .asSequence()
                        .filter { it.isRegularFile() }
                        .associateBy { relativePath(it) }
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
                val currentHash = sha256(path)
                if (
                    current.sha256 != currentHash ||
                    current.sizeBytes != path.fileSize() ||
                    current.mimeType != detectMimeType(path)
                ) {
                    store.saveWorkspaceFile(
                        current.copy(
                            mimeType = detectMimeType(path),
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
            .filter { normalizeRelativePath(it.relativePath) !in filesByRelative.keys }
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
     */
    fun requireFile(
        id: String?,
        path: String?,
    ): WorkspaceFileRecord =
        id?.let(store::getWorkspaceFile)
            ?: path?.let { store.getWorkspaceFileByPath(normalizeRelativePath(it)) }
            ?: throw IllegalArgumentException("Workspace file not found")

    /**
     * Deletes a managed file and its metadata.
     *
     * @param id Workspace file ID
     * @return true when a file was deleted
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
     */
    fun renameFile(
        id: String,
        requestedName: String,
    ): WorkspaceFileRecord {
        val current = store.getWorkspaceFile(id) ?: throw IllegalArgumentException("Workspace file not found")
        val source = resolveManagedPath(current.relativePath)
        val safeName = safeFileName(requestedName)
        val targetName = preserveExtensionIfMissing(source, safeName)
        val destination = uniqueDestination(source.parent, targetName)
        Files.move(source, destination)
        val updated =
            current.copy(
                relativePath = relativePath(destination),
                mimeType = detectMimeType(destination),
                sizeBytes = destination.fileSize(),
                sha256 = sha256(destination),
                updatedAt = Instant.now(),
            )
        store.saveWorkspaceFile(updated)
        return updated
    }

    /**
     * Computes the current SHA-256 hash for a managed file.
     */
    fun hash(record: WorkspaceFileRecord): String = sha256(resolveManagedPath(record.relativePath))

    /**
     * Reads bounded UTF-8 text from a managed file.
     */
    fun readText(record: WorkspaceFileRecord): String =
        resolveManagedPath(record.relativePath).readText(Charsets.UTF_8).take(MAX_TEXT_CHARS)

    /**
     * Extracts and caches text from a managed PDF.
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
            mimeType = detectMimeType(path),
            sizeBytes = path.fileSize(),
            sha256 = sha256(path),
        )
    }

    /**
     * Returns bounded base64 bytes for image/tool transport.
     */
    fun imageBytes(record: WorkspaceFileRecord): WorkspaceImageBytes {
        val path = resolveManagedPath(record.relativePath)
        require(path.fileSize() <= MAX_BASE64_BYTES) { "Image is larger than ${MAX_BASE64_BYTES / 1024 / 1024} MB" }
        return WorkspaceImageBytes(
            mimeType = detectMimeType(path),
            base64 = Base64.getEncoder().encodeToString(path.readBytes()),
        )
    }

    /**
     * Resolves a workspace-relative path and guarantees it stays inside the managed workspace.
     */
    fun resolveManagedPath(relativePath: String): Path {
        val resolved = workspaceRoot().resolve(normalizeRelativePath(relativePath)).normalize()
        require(resolved.startsWith(workspaceRoot())) { "Path escapes workspace root" }
        require(resolved.exists() && resolved.isRegularFile()) { "Workspace file does not exist" }
        return resolved
    }

    private fun normalizedDatabasePath(): Path {
        val raw = AppConfig.instance.databasePath.removePrefix("jdbc:sqlite:")
        val path = Path.of(raw.ifBlank { "./data/visual-agent.db" })
        return if (path.isAbsolute) path.normalize() else Path.of(System.getProperty("user.dir")).resolve(path).normalize()
    }

    private fun relativePath(path: Path): String = workspaceRoot().relativize(path.toAbsolutePath().normalize()).toString()

    private fun normalizeRelativePath(path: String): String = path.replace('\\', '/').trim().removePrefix("/")

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
                record.mimeType.startsWith("text/") || path.extension.lowercase() in TEXT_EXTENSIONS ->
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
            relativePath = relativePath(path),
            originalName = safeFileName(originalName),
            mimeType = detectMimeType(path),
            sizeBytes = path.fileSize(),
            sha256 = sha256(path),
            extractedText = null,
            importedAt = now,
            updatedAt = now,
        )
    }

    private fun safeDirectoryName(name: String): String =
        safeFileName(name)
            .replace(" ", "-")
            .ifBlank { "generated" }

    private fun uniqueDestination(
        directory: Path,
        requestedName: String,
    ): Path {
        val safeName = safeFileName(requestedName)
        val base = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "").let { if (it.isBlank() || it == safeName) "" else ".$it" }
        var candidate = directory.resolve("$base$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = directory.resolve("$base-$index$extension")
            index++
        }
        return candidate
    }

    private fun safeFileName(name: String): String =
        Path
            .of(name)
            .fileName
            .toString()
            .ifBlank { "file" }
            .replace(Regex("""[^\w.\- ]"""), "_")

    private fun preserveExtensionIfMissing(
        source: Path,
        requestedName: String,
    ): String =
        if (Path.of(requestedName).extension.isBlank() && source.extension.isNotBlank()) {
            "$requestedName.${source.extension}"
        } else {
            requestedName
        }

    private fun detectMimeType(path: Path): String =
        Files.probeContentType(path)
            ?: when (path.extension.lowercase()) {
                "txt", "md", "csv", "json", "xml", "kt", "java", "log" -> "text/plain"
                "draw", "jhd", "canvas" -> CANVAS_MIME_TYPE
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                else -> "application/octet-stream"
            }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 50L * 1024L * 1024L
        const val MAX_BASE64_BYTES = 8L * 1024L * 1024L
        const val MAX_TEXT_CHARS = 120_000
        const val MAX_SEARCH_RESULTS = 50
        const val CANVAS_MIME_TYPE = "application/vnd.visual-agent.canvas+xml"
        val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml", "log", "kt", "java", "draw", "jhd", "canvas")
    }
}

private fun String.snippet(index: Int): String {
    val start = (index - 80).coerceAtLeast(0)
    val end = (index + 160).coerceAtMost(length)
    return substring(start, end).replace(Regex("\\s+"), " ").trim()
}

/** Search result for workspace files. */
data class WorkspaceSearchResult(
    val query: String,
    val matches: List<WorkspaceSearchMatch>,
)

/** One workspace search hit. */
data class WorkspaceSearchMatch(
    val record: WorkspaceFileRecord,
    val matchType: String,
    val snippet: String,
)

/** Filesystem/DB reconciliation report. */
data class WorkspaceSyncResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val total: Int,
)

/** Extracted text result with cache state. */
data class WorkspaceFileText(
    val text: String,
    val cached: Boolean,
)

/** Image metadata returned to tools and UI consumers. */
data class WorkspaceImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** Bounded base64 image payload. */
data class WorkspaceImageBytes(
    val mimeType: String,
    val base64: String,
)
