package de.heckenmann.visualagent.protocol

/** Server-owned progress state for the versioned first-run provider onboarding. */
enum class OnboardingStatus {
    NOT_STARTED,
    DISMISSED,
    COMPLETED,
}

/** Versioned onboarding state read only after a Visual Agent server connection is ready. */
data class OnboardingState(
    val status: OnboardingStatus,
    val version: Int,
)

/** Safe provider information that never contains an existing credential. */
data class OnboardingProviderProfile(
    val id: String,
    val name: String,
    val adapter: ProviderAdapter,
    val baseUrl: String,
    val credentialConfigured: Boolean,
    val enabled: Boolean,
    val defaultModel: String,
    val models: List<ProviderModel>,
)

/** Write-only instruction for the credential of one staged provider configuration. */
sealed interface CredentialUpdate {
    /** Keeps the currently configured credential on the selected Visual Agent server. */
    data object Unchanged : CredentialUpdate

    /** Removes the credential from the selected Visual Agent server. */
    data object Clear : CredentialUpdate

    /** Replaces the credential with a value that exists only in the transient client draft. */
    data class Replace(
        val value: String,
    ) : CredentialUpdate
}

/** One staged provider profile sent to the connected Visual Agent server for onboarding. */
data class OnboardingProviderDraft(
    val id: String,
    val name: String,
    val adapter: ProviderAdapter,
    val baseUrl: String,
    val credential: CredentialUpdate = CredentialUpdate.Unchanged,
    val enabled: Boolean = true,
    val defaultModel: String = "",
)

/** Safe result of validating a staged provider or selected model. */
data class OnboardingValidationResult(
    val success: Boolean,
    val code: OnboardingValidationCode,
    val message: String,
    val validationFingerprint: String? = null,
)

/** Provider readiness outcomes exposed without raw SDK exceptions or secret values. */
enum class OnboardingValidationCode {
    SUCCESS,
    AUTHENTICATION_FAILED,
    ENDPOINT_UNAVAILABLE,
    INVALID_CONFIGURATION,
    PREREQUISITE_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    VALIDATION_UNSUPPORTED,
}

/** Transport-neutral contract for the onboarding state and staged provider setup. */
interface OnboardingPort {
    /** Reads the current versioned onboarding state from the connected Visual Agent server. */
    fun state(): OnboardingState

    /** Returns safe provider views without returning stored credential values. */
    fun providers(): List<OnboardingProviderProfile>

    /** Marks automatic onboarding as dismissed without changing provider configuration. */
    fun dismiss()

    /** Discovers structured selectable models for a staged provider without persisting the draft. */
    suspend fun discoverModels(draft: OnboardingProviderDraft): List<ProviderModel>

    /** Validates the staged provider and selected model without persisting the draft. */
    suspend fun validate(
        draft: OnboardingProviderDraft,
        modelId: String,
    ): OnboardingValidationResult

    /** Atomically commits a previously validated provider/model configuration and completion state. */
    suspend fun finish(
        draft: OnboardingProviderDraft,
        model: ProviderModel,
        validationFingerprint: String,
    )
}
