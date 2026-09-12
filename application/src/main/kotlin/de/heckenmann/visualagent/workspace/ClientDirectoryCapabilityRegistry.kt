package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.protocol.ClientDirectoryCapabilityRegistration
import de.heckenmann.visualagent.protocol.ClientDirectoryFileAccess
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks live client-owned directory capabilities without retaining client filesystem paths.
 *
 * A remote transport installs a proxy implementation of [ClientDirectoryFileAccess]; the local
 * desktop installs a direct adapter. Both are revoked when their connection closes.
 */
@Service
class ClientDirectoryCapabilityRegistry {
    private val capabilities = ConcurrentHashMap<String, Capability>()

    /**
     * Registers a capability for the lifetime of its owning client connection.
     *
     * A reconnect may replace the capability only when the persisted grant remains owned by the
     * same client. A different client must never be able to take over an existing grant ID.
     */
    fun register(
        registration: ClientDirectoryCapabilityRegistration,
        access: ClientDirectoryFileAccess,
    ) {
        capabilities.compute(registration.grantId) { _, current ->
            require(current == null || current.registration.clientId == registration.clientId) {
                "ACCESS_DENIED: directory grant belongs to another client"
            }
            Capability(registration, access)
        }
    }

    /** Returns whether a currently connected client still owns the exact capability. */
    fun isAvailable(
        grantId: String,
        clientId: String,
    ): Boolean =
        capabilities[grantId]?.let { capability ->
            capability.registration.clientId == clientId && capability.access.isAvailable()
        } == true

    /** Returns whether an exact registration is live on the current client connection. */
    fun isAvailable(registration: ClientDirectoryCapabilityRegistration): Boolean =
        capabilities[registration.grantId]?.let { capability ->
            capability.registration == registration && capability.access.isAvailable()
        } == true

    /** Resolves an active capability or fails without falling back to another client. */
    fun requireAccess(
        grantId: String,
        clientId: String,
    ): ClientDirectoryFileAccess {
        val capability = requireNotNull(capabilities[grantId]) { "CLIENT_DISCONNECTED: directory owner is unavailable" }
        require(capability.registration.clientId == clientId) { "ACCESS_DENIED: directory belongs to another client" }
        require(capability.access.isAvailable()) { "CLIENT_DISCONNECTED: directory owner is unavailable" }
        return capability.access
    }

    /** Immediately removes one capability when its grant is revoked. */
    fun revoke(grantId: String) {
        capabilities.remove(grantId)
    }

    /** Revokes every capability installed by one disconnected desktop client. */
    fun revokeClient(clientId: String) {
        capabilities.entries.removeIf { (_, capability) -> capability.registration.clientId == clientId }
    }

    private data class Capability(
        val registration: ClientDirectoryCapabilityRegistration,
        val access: ClientDirectoryFileAccess,
    )
}
