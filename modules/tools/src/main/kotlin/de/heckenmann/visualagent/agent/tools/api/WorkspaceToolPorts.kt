package de.heckenmann.visualagent.agent.tools.api

import java.time.Instant

/** Managed workspace-file operations needed by the workspace file tool. */
interface WorkspaceFileToolPort {
    /** Lists managed files. */
    fun list(): List<ToolWorkspaceFile>

    /** Lists managed workspace directories, including empty directories. */
    fun listDirectories(): List<String>

    /** Creates a directory in the managed workspace. */
    fun createDirectory(
        parentDirectory: String,
        name: String,
    ): String

    /** Searches managed files. */
    fun search(query: String): ToolWorkspaceSearch

    /** Synchronizes persisted metadata. */
    fun sync(): ToolWorkspaceSync

    /** Resolves one managed file. */
    fun requireFile(
        id: String?,
        path: String?,
    ): ToolWorkspaceFile

    /** Deletes one managed file and its persisted metadata. */
    fun delete(file: ToolWorkspaceFile): Boolean

    /** Deletes a managed workspace directory, optionally including descendants. */
    fun deleteDirectory(
        relativePath: String,
        recursive: Boolean = false,
    ): ToolWorkspaceDirectoryDeletion

    /** Hashes one file. */
    fun hash(file: ToolWorkspaceFile): String

    /** Reads bounded text. */
    fun readText(file: ToolWorkspaceFile): String

    /** Extracts PDF text. */
    fun extractPdfText(file: ToolWorkspaceFile): ToolExtractedText

    /** Renders one PDF page. */
    fun renderPdfPage(
        file: ToolWorkspaceFile,
        page: Int,
    ): ToolWorkspaceFile

    /** Reads image metadata. */
    fun imageInfo(file: ToolWorkspaceFile): ToolImageInfo

    /** Reads encoded image bytes. */
    fun imageBytes(file: ToolWorkspaceFile): ToolImageBytes

    /** Analyzes an image through the active model. */
    fun analyzeImage(
        file: ToolWorkspaceFile,
        prompt: String,
    ): ToolImageAnalysis

    /** Detects a managed file MIME type from its content rather than its name. */
    fun detectMimeType(file: ToolWorkspaceFile): ToolMimeType

    /** Downloads a remote resource into the managed workspace. */
    fun download(request: ToolDownloadRequest): ToolWorkspaceFile = error("Workspace downloads are not configured")
}

/** Managed workspace-file projection. */
data class ToolWorkspaceFile(
    val id: String,
    val relativePath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val hasExtractedText: Boolean,
    val importedAt: Instant,
    val updatedAt: Instant,
)

/** One workspace search match. */
data class ToolWorkspaceMatch(
    val matchType: String,
    val snippet: String,
    val file: ToolWorkspaceFile,
)

/** Workspace search result. */
data class ToolWorkspaceSearch(
    val query: String,
    val matches: List<ToolWorkspaceMatch>,
)

/** Workspace synchronization counts. */
data class ToolWorkspaceSync(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val total: Int,
)

/** Result of deleting a managed workspace directory. */
data class ToolWorkspaceDirectoryDeletion(
    val relativePath: String,
    val recursive: Boolean,
    val deletedFiles: Int,
    val deletedMetadata: Int,
)

/** Extracted PDF text and cache state. */
data class ToolExtractedText(
    val cached: Boolean,
    val text: String,
)

/** Safe image metadata. */
data class ToolImageInfo(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val sha256: String,
)

/** Base64 image payload. */
data class ToolImageBytes(
    val mimeType: String,
    val base64: String,
)

/** Vision-model response projection. */
data class ToolImageAnalysis(
    val model: String,
    val content: String,
)

/** Content-derived MIME metadata for a managed workspace file. */
data class ToolMimeType(
    val detectedMimeType: String,
    val storedMimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** Model-requested remote workspace download. */
data class ToolDownloadRequest(
    val source: String,
    val directory: String?,
    val filename: String?,
)

/** Workspace-layout operations needed by the layout tool. */
interface WorkspaceLayoutToolPort {
    /** Serializes the current public layout report. */
    fun reportJson(): String

    /** Reads mutable window states. */
    fun windows(): List<ToolWindowState>

    /** Applies states and serializes the resulting layout. */
    fun apply(windows: List<ToolWindowState>): String
}

/** Tool-owned mutable workspace-window state. */
data class ToolWindowState(
    val id: String,
    val order: Int,
    val visible: Boolean,
    val preferredWidth: Double,
)
