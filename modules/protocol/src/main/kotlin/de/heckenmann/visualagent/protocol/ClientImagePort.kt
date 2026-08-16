package de.heckenmann.visualagent.protocol

/**
 * Resolves image files that belong to the client presentation environment.
 *
 * The UI must use this port only for explicitly `client-file:` sources. All
 * other image sources are resolved by [ConversationPort] on the application
 * server so that a remote desktop never reads a server path accidentally.
 */
interface ClientImagePort {
    /** Resolves one explicitly client-local image source. */
    suspend fun resolveImage(source: String): ConversationImageResolution
}
