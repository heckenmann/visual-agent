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
    val ownerLabel: String,
    val mode: DirectoryAccessMode,
    val location: String?,
    val available: Boolean,
    val diagnostic: String?,
)

/** One server-owned directory offered by the direct-user directory picker. */
data class ServerDirectoryPickerEntry(
    val selectionId: String,
    val displayName: String,
    val location: String,
    val parentSelectionId: String?,
)

/** One bounded page from the direct-user server directory browser. */
data class ServerDirectoryPickerPage(
    val entries: List<ServerDirectoryPickerEntry>,
    val nextPageToken: String?,
)

/**
 * Direct-user-only management boundary for additional filesystem directory grants.
 *
 * This port is deliberately not part of the model tool surface. Only an authenticated
 * presentation client may offer explicit user actions that call its mutating operations.
 */
interface DirectoryGrantAdministrationPort {
    /** Lists persisted grants and refreshes their safe availability state. */
    fun listGrants(): List<DirectoryGrantView>

    /** Lists the roots that the direct user may browse on the application server. */
    fun listServerDirectoryRoots(pageToken: String? = null): ServerDirectoryPickerPage

    /** Lists child directories of a previously issued opaque server selection. */
    fun listServerDirectoryChildren(
        selectionId: String,
        pageToken: String? = null,
    ): ServerDirectoryPickerPage

    /** Creates a server-owned directory grant after explicit user confirmation. */
    fun addServerGrant(
        selectionId: String,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrantView

    /** Persists a capability that was registered by the filesystem-owning desktop client. */
    fun addClientGrant(
        registration: ClientDirectoryCapabilityRegistration,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrantView

    /** Reattaches a newly negotiated client capability to its existing grant. */
    fun reactivateClientGrant(registration: ClientDirectoryCapabilityRegistration): DirectoryGrantView

    /** Updates user-controlled grant metadata without changing its root or origin. */
    fun updateGrant(
        id: String,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrantView

    /** Immediately revokes a grant. The UI must confirm this destructive action first. */
    fun removeGrant(id: String): Boolean
}
