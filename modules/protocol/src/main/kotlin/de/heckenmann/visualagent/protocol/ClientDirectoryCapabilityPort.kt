package de.heckenmann.visualagent.protocol

/** Opaque registration emitted by the filesystem-owning desktop client. */
data class ClientDirectoryCapabilityRegistration(
    val grantId: String,
    val clientId: String,
    val capabilityId: String,
)

/** Safe metadata for one file or directory owned by a connected client. */
data class ClientDirectoryEntry(
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long?,
)

/** One bounded text-search match returned by a connected client. */
data class ClientDirectoryMatch(
    val path: String,
    val line: Int,
    val snippet: String,
)

/**
 * Filesystem operations supplied by the exact desktop client that owns a directory capability.
 *
 * The server selects this capability only by opaque identifiers. The implementation owns path
 * canonicalization and must never accept a host path from the server.
 */
interface ClientDirectoryFileAccess {
    /** Returns whether the capability remains available on its filesystem owner. */
    fun isAvailable(): Boolean

    /** Lists an authorized client-relative directory. */
    fun list(relativePath: String): List<ClientDirectoryEntry>

    /** Reads bounded UTF-8 text from an authorized client-relative file. */
    fun readText(relativePath: String): String

    /** Reads at most [maximumBytes] from an authorized client-relative binary file. */
    fun readBytes(
        relativePath: String,
        maximumBytes: Long,
    ): ByteArray

    /** Searches bounded UTF-8 files beneath the client-owned root. */
    fun search(
        query: String,
        path: String,
    ): List<ClientDirectoryMatch>

    /** Finds regular files below [path] whose root-relative path matches [pattern]. */
    fun glob(
        path: String,
        pattern: String,
    ): List<ClientDirectoryEntry>

    /** Writes bounded UTF-8 text beneath a read-write client-owned root. */
    fun writeText(
        relativePath: String,
        content: String,
    ): String

    /** Creates a client-relative directory after capability-side authorization. */
    fun createDirectory(relativePath: String): String

    /** Deletes a client-relative file or directory after capability-side authorization. */
    fun delete(
        relativePath: String,
        recursive: Boolean,
    )
}

/** Client-local adapter that prepares a grant after the user picked a local directory. */
interface ClientDirectoryGrantAdministrationPort {
    /** Registers a locally canonicalized directory and returns no host path to the server. */
    fun prepareDirectoryGrant(
        absolutePath: String,
        mode: DirectoryAccessMode,
    ): ClientDirectoryCapabilityRegistration

    /** Revalidates a prior client-owned grant and registers a fresh session capability. */
    fun reactivateDirectoryGrant(
        grantId: String,
        absolutePath: String,
        mode: DirectoryAccessMode,
    ): ClientDirectoryCapabilityRegistration

    /** Revokes a client-local root after its server grant was removed. */
    fun revokeDirectoryGrant(grantId: String)
}
