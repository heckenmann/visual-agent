package de.heckenmann.visualagent.protocol

/**
 * Transport-neutral reference to a file or directory below an opaque root.
 *
 * @property rootId Managed-workspace or directory-grant identifier
 * @property relativePath Path relative to [rootId], never a host path
 */
data class FileReference(
    val rootId: String,
    val relativePath: String,
)
