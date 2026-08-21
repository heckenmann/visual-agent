package de.heckenmann.visualagent.protocol

/** Workspace file operations used by the presentation client. */
interface WorkspaceFilePort {
    /** Returns the displayable managed workspace root. */
    fun workspaceRoot(): String

    /** Lists persisted workspace file metadata. */
    fun listFiles(): List<WorkspaceFile>

    /** Lists workspace-relative directories, including empty directories. */
    fun listDirectories(): List<String>

    /** Creates a directory below the supplied workspace-relative parent directory. */
    fun createDirectory(
        parentDirectory: String,
        name: String,
    ): String

    /** Imports an external file into a workspace-relative directory. */
    fun importFile(
        directory: String,
        name: String,
        bytes: ByteArray,
    ): WorkspaceFile

    /** Returns downloads that are currently running or paused. */
    fun activeDownloads(): List<WorkspaceDownload> = emptyList()

    /** Pauses one active download. */
    fun pauseDownload(id: String) = Unit

    /** Resumes one paused download. */
    fun resumeDownload(id: String) = Unit

    /** Cancels one active download and removes its partial workspace data. */
    fun cancelDownload(id: String) = Unit

    /** Registers a listener for download progress changes. */
    fun addDownloadListener(listener: () -> Unit): AutoCloseable

    /** Creates a generated managed file. */
    fun createManagedFile(
        directoryName: String,
        requestedName: String,
        bytes: ByteArray,
        mimeType: String? = null,
    ): WorkspaceFile

    /** Synchronizes persisted metadata with files on disk. */
    fun syncMetadataWithFilesystem(): WorkspaceSyncResult

    /** Renames one managed file. */
    fun renameFile(
        id: String,
        requestedName: String,
    ): WorkspaceFile

    /** Deletes one managed file. */
    fun deleteFile(id: String): Boolean

    /** Reads bytes from a managed file by relative path. */
    fun readBytes(relativePath: String): ByteArray

    /** Registers a listener for server-side workspace changes. */
    fun addListener(listener: () -> Unit): AutoCloseable
}

/** Persisted workspace file metadata exposed to the UI. */
data class WorkspaceFile(
    val id: String,
    val relativePath: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val importedAt: String,
    val updatedAt: String,
)

/** Counts produced by workspace metadata synchronization. */
data class WorkspaceSyncResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val total: Int,
)

/** Server-owned download progress exposed to the file browser. */
data class WorkspaceDownload(
    val id: String,
    val relativePath: String,
    val state: WorkspaceDownloadState,
    val downloadedBytes: Long,
    val totalBytes: Long? = null,
)

/** State of one server-owned workspace download. */
enum class WorkspaceDownloadState {
    DOWNLOADING,
    PAUSED,
}
