package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryGrantOrigin
import java.time.Instant

/** Authorized directory grant used inside the server boundary. */
data class DirectoryGrant(
    val id: String,
    val displayName: String,
    val canonicalRoot: String?,
    val origin: DirectoryGrantOrigin,
    val mode: DirectoryAccessMode,
    val clientBindingId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Safe grant-relative entry metadata exposed to model tools. */
data class GrantedDirectoryEntry(
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long?,
)

/** One bounded text-search match inside a granted root. */
data class GrantedDirectoryMatch(
    val path: String,
    val line: Int,
    val snippet: String,
)
