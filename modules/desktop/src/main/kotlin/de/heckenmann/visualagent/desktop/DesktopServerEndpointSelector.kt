package de.heckenmann.visualagent.desktop

import java.net.URI

/** Reads endpoint selection from bootstrap properties before any server contact. */
object DesktopServerEndpointSelector {
    /** System property for an explicit remote endpoint, for example `grpcs://host:7443`. */
    const val REMOTE_ENDPOINT_PROPERTY = "visualagent.server.remote-endpoint"

    /** System property for the local in-process server name. */
    const val LOCAL_NAME_PROPERTY = "visualagent.server.in-process-name"

    /** Selects one explicit endpoint; a failed remote connection never falls back to local. */
    fun select(properties: Map<String, String>): DesktopServerEndpoint {
        val remote = properties[REMOTE_ENDPOINT_PROPERTY]?.trim().orEmpty()
        if (remote.isBlank()) {
            return DesktopServerEndpoint.LocalInProcess(
                properties[LOCAL_NAME_PROPERTY]?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_LOCAL_NAME,
            )
        }
        val uri = URI(remote)
        require(uri.scheme == REMOTE_SCHEME) { "Remote endpoint must use grpcs://" }
        require(!uri.host.isNullOrBlank() && uri.port in 1..65535) {
            "Remote endpoint must contain a host and valid port"
        }
        return DesktopServerEndpoint.RemoteTls(uri.host, uri.port)
    }

    private const val REMOTE_SCHEME = "grpcs"
    private const val DEFAULT_LOCAL_NAME = "visual-agent-local"
}
