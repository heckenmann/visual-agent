package de.heckenmann.visualagent.protocol

/** Explicit source prefixes used to keep server and client file paths distinct. */
object ConversationImageSources {
    /** Prefix for a file that is read by the client presentation process. */
    const val CLIENT_FILE_PREFIX = "client-file:"

    /** Prefix for a registered file managed by the application server. */
    const val SERVER_FILE_PREFIX = "server-file:"

    /** Prefix for a registered workspace file managed by the application server. */
    const val WORKSPACE_PREFIX = "workspace:"

    /** Prefix for an opaque root ID and root-relative path resolved by the application server. */
    const val GRANTED_FILE_PREFIX = "visual-agent-file:"

    /** Returns whether [source] explicitly targets the client filesystem. */
    fun isClientFile(source: String): Boolean = source.trim().startsWith(CLIENT_FILE_PREFIX, ignoreCase = true)
}
