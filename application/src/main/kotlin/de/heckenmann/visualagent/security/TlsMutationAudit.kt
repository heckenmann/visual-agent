package de.heckenmann.visualagent.security

import de.heckenmann.visualagent.agent.tools.ServerTlsStore
import mu.KotlinLogging
import org.springframework.stereotype.Component

/** Writes secret-safe audit records for server-managed TLS store mutations. */
@Component
class TlsMutationAudit {
    private val logger = KotlinLogging.logger {}

    /** Records one mutation result while excluding certificate material, credentials, and raw errors. */
    fun <T> record(
        operation: String,
        store: ServerTlsStore,
        alias: String,
        fingerprint: () -> String?,
        outcome: (T) -> String = { "success" },
        action: () -> T,
    ): T {
        val safeAlias = alias.takeIf { it.matches(ALIAS_PATTERN) } ?: "<invalid>"
        return try {
            val result = action()
            val certificateFingerprint = runCatching(fingerprint).getOrNull() ?: "unavailable"
            logger.info {
                "TLS store mutation operation=$operation store=${store.name.lowercase()} alias=$safeAlias " +
                    "fingerprint=$certificateFingerprint outcome=${outcome(result)}"
            }
            result
        } catch (error: Exception) {
            val certificateFingerprint = runCatching(fingerprint).getOrNull() ?: "unavailable"
            logger.warn {
                "TLS store mutation operation=$operation store=${store.name.lowercase()} alias=$safeAlias " +
                    "fingerprint=$certificateFingerprint outcome=failed errorType=${error.javaClass.simpleName}"
            }
            throw error
        }
    }

    private companion object {
        val ALIAS_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    }
}
