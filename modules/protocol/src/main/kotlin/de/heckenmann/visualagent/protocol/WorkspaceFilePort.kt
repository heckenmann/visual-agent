package de.heckenmann.visualagent.protocol

/** Workspace file operations used by the presentation client. */
interface WorkspaceFilePort {
    /** Returns the displayable managed workspace root. */
    fun workspaceRoot(): String

    /** Lists persisted workspace file metadata. */
    fun listFiles(): List<WorkspaceFile>

    /** Imports an external file from its name and bytes. */
    fun importFile(
        name: String,
        bytes: ByteArray,
    ): WorkspaceFile

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
