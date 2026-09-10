package de.heckenmann.visualagent.agent.tools.api

/** Model-safe projection of a persisted directory grant. */
data class ToolDirectoryGrant(
    val id: String,
    val displayName: String,
    val origin: String,
    val mode: String,
    val available: Boolean,
)

/** Model-safe projection of an entry beneath a grant. */
data class ToolDirectoryEntry(
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long?,
)

/** Model-safe text-search match beneath a grant. */
data class ToolDirectoryMatch(
    val path: String,
    val line: Int,
    val snippet: String,
)

/** Operations available to the directory grant tool. */
interface DirectoryToolPort {
    /** Lists safe grant metadata without native paths. */
    fun listGrants(): List<ToolDirectoryGrant>

    /** Lists entries under one grant-relative directory. */
    fun list(
        grantId: String,
        path: String,
    ): List<ToolDirectoryEntry>

    /** Reads one bounded grant-relative text file. */
    fun readText(
        grantId: String,
        path: String,
    ): String

    /** Searches bounded grant contents. */
    fun search(
        grantId: String,
        query: String,
    ): List<ToolDirectoryMatch>

    /** Writes one bounded text file when the grant is read-write. */
    fun writeText(
        grantId: String,
        path: String,
        content: String,
    ): String
}
