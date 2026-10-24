package de.heckenmann.visualagent.protocol

/** Provider catalog and model discovery operations exposed to the UI. */
interface ProviderPort {
    /** Lists every configured provider profile. */
    fun listProviders(): List<ProviderProfile>

    /** Lists enabled provider profiles. */
    fun enabledProviders(): List<ProviderProfile>

    /** Returns one provider profile, or null when absent. */
    fun getProvider(id: String): ProviderProfile?

    /** Returns models selectable for one provider. */
    fun selectableModels(providerId: String): List<ProviderModel>

    /** Returns the active provider identifier. */
    fun activeProviderId(): String

    /** Returns the active model identifier. */
    fun activeModelId(): String

    /** Activates a provider without selecting an explicit model. */
    fun setActiveProvider(providerId: String)

    /** Activates a provider/model selection. */
    fun setActiveSelection(
        providerId: String,
        modelId: String,
    )

    /** Persists or replaces a provider profile. */
    fun saveProvider(profile: ProviderProfile)

    /** Saves an edited catalog without changing the live Conversation selection; rejects stale drafts. */
    fun replaceProfiles(
        profiles: List<ProviderProfile>,
        expectedProfiles: List<ProviderProfile>,
    ): Unit = throw UnsupportedOperationException("Provider catalog editing is unavailable")

    /** Deletes a provider profile when the catalog allows it. */
    fun deleteProvider(providerId: String): Boolean

    /** Discovers models and updates the provider catalog. */
    suspend fun refreshModels(providerId: String): List<ProviderModel>

    /** Discovers models with a staged provider profile without persisting the result. */
    suspend fun discoverModels(profile: ProviderProfile): List<ProviderModel>

    /** Loads details for one provider model. */
    suspend fun modelDetails(
        providerId: String,
        modelId: String,
    ): ModelDetails

    /**
     * Sends a small real request to verify the selected model, rather than checking metadata only.
     * Unsupported probe protocols are reported explicitly.
     */
    suspend fun probeModelCapability(
        providerId: String,
        modelId: String,
        capability: String,
    ): String = throw UnsupportedOperationException("Functional model probing is not available from this provider connection")

    /** Registers a listener for catalog changes. */
    fun addChangeListener(listener: () -> Unit): AutoCloseable

    /** Starts an explicit, server-owned ChatGPT authorization attempt. Never starts inference. */
    suspend fun beginDirectSignIn(profile: ProviderProfile): ProviderSignInSession =
        throw UnsupportedOperationException("Direct provider authorization is unavailable")

    /** Awaits validated account authorization without returning tokens. */
    suspend fun awaitDirectSignIn(sessionId: String): ProviderSignInResult =
        throw UnsupportedOperationException("Direct provider authorization is unavailable")

    /** Cancels an outstanding browser authorization attempt. */
    suspend fun cancelDirectSignIn(sessionId: String): Unit =
        throw UnsupportedOperationException("Direct provider authorization is unavailable")

    /** Signs out the selected OAuth profile and reports whether remote revocation was confirmed. */
    suspend fun signOutDirectProvider(profile: ProviderProfile): Boolean =
        throw UnsupportedOperationException("Direct provider authorization is unavailable")
}

/** Browser instructions; authorization URLs may contain identity hints and must never be logged. */
data class ProviderSignInSession(
    val id: String,
    val authorizationUrl: String,
) {
    override fun toString(): String = "ProviderSignInSession(redacted)"
}

/** Safe account-registration reference returned after successful server-side validation. */
data class ProviderSignInResult(
    val credentialReference: String,
    val accountLabel: String,
)

/** Supported provider adapter. */
enum class ProviderAdapter {
    OLLAMA,
    OPENAI_COMPATIBLE,
    CODEX_CLI,
    CODEX_DIRECT,
}

/** Availability state used for model selection. */
enum class ModelStatus {
    ACTIVE,
    ALPHA,
    BETA,
    DEPRECATED,
    DISABLED,
}

/** Configured provider profile. */
enum class DirectProviderAuthMode {
    API_KEY,
    CHATGPT_OAUTH,
}

/** Configured provider profile; OAuth secrets are represented only by an opaque server reference. */
data class ProviderProfile(
    val id: String,
    val name: String,
    val adapter: ProviderAdapter,
    val baseUrl: String,
    val apiKey: String = "",
    val enabled: Boolean = true,
    val defaultModel: String = "",
    val options: Map<String, String> = emptyMap(),
    val models: List<ProviderModel> = emptyList(),
    val modelWhitelist: Set<String> = emptySet(),
    val modelBlacklist: Set<String> = emptySet(),
    val directAuthMode: DirectProviderAuthMode = DirectProviderAuthMode.API_KEY,
    val credentialReference: String? = null,
)

/** Configured model metadata. */
data class ProviderModel(
    val id: String,
    val name: String = id,
    val status: ModelStatus = ModelStatus.ACTIVE,
    val options: Map<String, String> = emptyMap(),
    val variants: Map<String, Map<String, String>> = emptyMap(),
    val contextLimit: Int? = null,
    val outputLimit: Int? = null,
    val capabilities: Set<String> = emptySet(),
    val capabilitiesComplete: Boolean = false,
    val capabilitiesOverridden: Boolean = false,
    val capabilityOverrides: Map<String, Boolean> = emptyMap(),
)

/** User-safe model detail response. */
data class ModelDetails(
    val model: String,
    val modifiedAt: String,
    val family: String? = null,
    val parameterSize: String? = null,
    val format: String? = null,
    val quantizationLevel: String? = null,
    val contextLimit: Int? = null,
)
