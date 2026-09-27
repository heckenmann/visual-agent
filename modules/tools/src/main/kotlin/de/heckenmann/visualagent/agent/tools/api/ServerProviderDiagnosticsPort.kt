package de.heckenmann.visualagent.agent.tools.api

import reactor.core.publisher.Mono

/** Sanitized result of a live request to the active provider. */
data class ServerProviderDiagnostic(
    /** Active provider profile identifier. */
    val providerId: String,
    /** Protocol adapter used by the active provider profile. */
    val adapter: String,
    /** Whether the active profile is enabled. */
    val enabled: Boolean,
    /** Whether a credential is configured, never the credential itself. */
    val credentialConfigured: Boolean,
    /** Number of model identifiers returned by the live provider request. */
    val discoveredModelCount: Int?,
    /** Whether the configured model was present in that provider response. */
    val selectedModelAvailable: Boolean?,
    /** Normalized error category; never a raw provider response. */
    val failureKind: String?,
    /** Endpoint scheme and authority with userinfo, path, query, and fragment removed. */
    val endpointOrigin: String? = null,
    /** Configured model identifier checked against the live response. */
    val selectedModelId: String? = null,
    /** Provider-reported capabilities of the selected model, if found. */
    val selectedModelCapabilities: List<String>? = null,
    /** Whether the provider declares the selected model's capability list complete. */
    val selectedModelCapabilitiesComplete: Boolean? = null,
)

/** Performs a bounded, read-only connection check against the active configured provider. */
fun interface ServerProviderDiagnosticsPort {
    /** Queries the active provider model endpoint without mutating the persisted catalog. */
    fun check(): Mono<ServerProviderDiagnostic>
}
