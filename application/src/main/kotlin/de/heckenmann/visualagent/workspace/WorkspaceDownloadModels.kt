package de.heckenmann.visualagent.workspace

/** User-requested remote workspace download. */
data class WorkspaceDownloadRequest(
    val source: String,
    val directory: String? = null,
    val filename: String? = null,
)

/** Internal state for one active workspace download. */
internal data class WorkspaceDownloadJob(
    val id: String,
    val relativePath: String,
    val control: WorkspaceDownloadControl,
    @Volatile var paused: Boolean = false,
)

/** Content-derived MIME metadata for one managed workspace file. */
data class WorkspaceMimeTypeInfo(
    val detectedMimeType: String,
    val storedMimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** Destination and source details normalized before a transfer starts. */
internal data class WorkspaceDownloadTarget(
    val source: java.net.URI,
    val directory: java.nio.file.Path,
    val requestedName: String,
)
