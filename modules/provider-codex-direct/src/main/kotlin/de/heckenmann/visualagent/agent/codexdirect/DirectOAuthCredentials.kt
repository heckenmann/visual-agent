package de.heckenmann.visualagent.agent.codexdirect

import kotlinx.serialization.Serializable
import java.nio.file.Path

/** Server-owned location supplied by application bootstrap, never by a provider profile. */
data class DirectCredentialDirectory(
    val path: Path,
)

/** Validated OAuth account record; deliberately excludes token values from diagnostic strings. */
@Serializable
internal data class DirectOAuthCredentials(
    val profileId: String,
    val clientId: String,
    val subject: String,
    val issuer: String,
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresAtEpochSeconds: Long,
    val scopes: Set<String>,
    val generation: Long = 0,
) {
    override fun toString(): String = "DirectOAuthCredentials(redacted)"
}
