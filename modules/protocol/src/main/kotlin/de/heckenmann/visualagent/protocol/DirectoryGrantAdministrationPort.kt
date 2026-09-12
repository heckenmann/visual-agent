package de.heckenmann.visualagent.protocol

/** Permission assigned to one explicitly approved directory root. */
enum class DirectoryAccessMode { READ_ONLY, READ_WRITE }

/** Filesystem owner for a directory grant. */
enum class DirectoryGrantOrigin { SERVER, CLIENT }

/** User-facing directory grant state returned through the protocol boundary. */
data class DirectoryGrantView(
    val id: String,
    val displayName: String,
    val origin: DirectoryGrantOrigin,
    val mode: DirectoryAccessMode,
    val location: String?,
    val available: Boolean,
    val diagnostic: String?,
)

/** Direct-user management boundary for additional filesystem directory grants. */
interface DirectoryAccessPort {
    /** Lists persisted grants and refreshes their safe availability state. */
    fun listGrants(): List<DirectoryGrantView>

    /** Resolves and validates a proposed server directory without granting it. */
    fun inspectServerDirectory(absolutePath: String): DirectoryGrantView

    /** Creates a server-owned directory grant after explicit user confirmation. */
    fun addServerGrant(
        absolutePath: String,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrantView

    /** Updates user-controlled grant metadata without changing its root or origin. */
    fun updateGrant(
        id: String,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrantView

    /** Immediately revokes a grant. The UI must confirm this destructive action first. */
    fun removeGrant(id: String): Boolean
}
