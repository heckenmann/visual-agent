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

/** Content-derived MIME information for a file beneath a grant. */
data class ToolDirectoryMimeType(
    val detectedMimeType: String,
    val inspectedBytes: Int,
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

    /** Detects a MIME type from bounded content through the filesystem owner's capability. */
    fun detectMimeType(
        grantId: String,
        path: String,
    ): ToolDirectoryMimeType

    /** Searches bounded grant contents. */
    fun search(
        grantId: String,
        query: String,
        path: String,
    ): List<ToolDirectoryMatch>

    /** Finds regular files below [path] whose root-relative path matches [pattern]. */
    fun glob(
        grantId: String,
        path: String,
        pattern: String,
    ): List<ToolDirectoryEntry>

    /** Writes one bounded text file when the grant is read-write. */
    fun writeText(
        grantId: String,
        path: String,
        content: String,
    ): String

    /** Replaces one exact occurrence of [oldText] in a grant-relative text file. */
    fun editText(
        grantId: String,
        path: String,
        oldText: String,
        newText: String,
    ): String

    /** Creates a grant-relative directory. */
    fun createDirectory(
        grantId: String,
        path: String,
    ): String

    /** Deletes a grant-relative file or directory. */
    fun delete(
        grantId: String,
        path: String,
        recursive: Boolean,
    )

    /** Copies one regular file between explicitly granted directory roots. */
    fun copy(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ): String

    /** Moves one regular file between explicitly granted directory roots after verifying the copy. */
    fun move(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ): String
}
