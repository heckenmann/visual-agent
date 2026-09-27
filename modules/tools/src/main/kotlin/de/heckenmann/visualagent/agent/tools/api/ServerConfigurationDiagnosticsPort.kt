package de.heckenmann.visualagent.agent.tools.api

/** Sanitized view of the active provider profile and model. */
data class ProviderConfigurationDiagnostic(
    /** Stable active provider identifier. */
    val providerId: String,
    /** Provider protocol adapter name. */
    val adapter: String?,
    /** Whether the active provider profile is enabled. */
    val enabled: Boolean,
    /** Endpoint origin only, without path, credentials, query, or fragment. */
    val endpointOrigin: String?,
    /** Whether an API key is configured, never the key itself. */
    val apiKeyConfigured: Boolean,
    /** Active model identifier. */
    val modelId: String,
    /** Model context limit declared by the provider catalog. */
    val modelContextLimit: Int?,
    /** Model output limit declared by the provider catalog. */
    val modelOutputLimit: Int?,
    /** Explicitly declared model capabilities. */
    val modelCapabilities: List<String>,
    /** Whether the provider supplied a complete capability declaration. */
    val modelCapabilitiesComplete: Boolean,
)

/** Sanitized server configuration used by the model-facing diagnostics tool. */
data class EffectiveServerConfigurationDiagnostic(
    /** Active provider and model details. */
    val provider: ProviderConfigurationDiagnostic,
    /** Configured conversation context window in tokens. */
    val contextWindow: Int,
    /** Configured provider/tool timeout in seconds. */
    val timeoutSeconds: Int,
    /** Maximum number of concurrently scheduled sub-agent jobs. */
    val maxParallelSubAgents: Int,
    /** Non-secret validation warnings about the active configuration. */
    val warnings: List<String>,
    /** Stable warning identifiers suitable for aggregate health results. */
    val warningCodes: List<String> = emptyList(),
)

/** Supplies a sanitized effective server configuration without exposing secrets or local paths. */
fun interface ServerConfigurationDiagnosticsPort {
    /** Returns the latest DB-backed provider selection and runtime configuration. */
    fun snapshot(): EffectiveServerConfigurationDiagnostic
}
