package de.heckenmann.visualagent.protocol

/** Result of resolving one Markdown image through a server or client media boundary. */
sealed interface ConversationImageResolution {
    /** A validated image payload safe for presentation rendering. */
    data class Loaded(
        /** Validated image MIME type. */
        val mimeType: String,
        /** Image bytes returned by the server. */
        val bytes: ByteArray,
    ) : ConversationImageResolution

    /** A safe, user-displayable rejection without server or network details. */
    data class Rejected(
        /** Short reason used by the presentation fallback. */
        val reason: String,
    ) : ConversationImageResolution
}
