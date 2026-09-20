package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelParameters
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.protocol.CredentialUpdate
import de.heckenmann.visualagent.protocol.OnboardingAgent
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationResult
import de.heckenmann.visualagent.protocol.OnboardingPort
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingProviderProfile
import de.heckenmann.visualagent.protocol.OnboardingState
import de.heckenmann.visualagent.protocol.OnboardingStatus
import de.heckenmann.visualagent.protocol.OnboardingValidationCode
import de.heckenmann.visualagent.protocol.OnboardingValidationResult
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Component
import java.security.MessageDigest
import de.heckenmann.visualagent.agent.provider.ProviderAdapter as ApplicationProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderConfiguration as ApplicationProviderConfiguration
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig as ApplicationProviderModel
import de.heckenmann.visualagent.agent.provider.ProviderProfile as ApplicationProviderProfile

/** Implements secure, server-owned first-run provider/model onboarding. */
@Component
class SpringOnboardingPort(
    private val preferenceStore: PreferenceStore,
    private val providerCatalog: ProviderCatalogService,
    private val llmProvider: LLMProvider,
    private val onboardingAgentService: OnboardingAgentService,
) : OnboardingPort {
    override fun state(): OnboardingState =
        OnboardingState(
            status =
                preferenceStore
                    .getPreference(ONBOARDING_KEY)
                    ?.let { stored ->
                        runCatching { OnboardingStatus.valueOf(stored) }
                            .getOrElse { throw IllegalStateException("The stored onboarding state is invalid.") }
                    }
                    ?: OnboardingStatus.NOT_STARTED,
            version = ONBOARDING_VERSION,
        )

    override fun providers(): List<OnboardingProviderProfile> = providerCatalog.listProviders().map { profile -> profile.toSafeView() }

    override fun agents(): List<OnboardingAgent> = onboardingAgentService.agents()

    override fun dismiss() {
        preferenceStore.setPreference(ONBOARDING_KEY, OnboardingStatus.DISMISSED.name)
    }

    override suspend fun discoverModels(draft: OnboardingProviderDraft): List<ProviderModel> =
        llmProvider
            .getModelConfigsReactive(draft.toApplication(providerCatalog.getProvider(draft.id)))
            .awaitSingle()
            .distinctBy { it.id }
            .map { model -> model.toProtocol() }
            .filter { model -> model.status != de.heckenmann.visualagent.protocol.ModelStatus.DISABLED }

    override suspend fun validate(
        draft: OnboardingProviderDraft,
        modelId: String,
    ): OnboardingValidationResult =
        try {
            require(modelId.isNotBlank()) { "Select a model before continuing." }
            val models = discoverModels(draft)
            if (models.none { it.id == modelId }) {
                OnboardingValidationResult(false, OnboardingValidationCode.MODEL_UNAVAILABLE, "The selected model is not available.")
            } else {
                val profile = draft.toApplication(providerCatalog.getProvider(draft.id))
                llmProvider
                    .chatReactive(
                        ChatRequestContext(
                            messages = listOf(Message(role = "user", content = "Reply with READY.")),
                            provider = profile.id,
                            model = modelId,
                            parameters = ModelParameters(maxTokens = READINESS_MAX_TOKENS),
                            providerProfile = profile,
                        ),
                    ).awaitSingle()
                OnboardingValidationResult(
                    true,
                    OnboardingValidationCode.SUCCESS,
                    "Provider and selected model are ready.",
                    fingerprint(draft, modelId),
                )
            }
        } catch (failure: Exception) {
            OnboardingValidationResult(false, failure.toValidationCode(), failure.toSafeMessage())
        }

    override suspend fun finish(
        draft: OnboardingProviderDraft,
        model: ProviderModel,
        validationFingerprint: String,
    ) {
        val validation = validate(draft, model.id)
        require(validation.success && validation.validationFingerprint == validationFingerprint) {
            "The provider draft changed or is no longer ready and must be validated again."
        }
        val discoveredModel =
            discoverModels(draft).singleOrNull { it.id == model.id }
                ?: error("The selected model is no longer available.")
        val existing = providerCatalog.getProvider(draft.id)
        val selectedModel = discoveredModel.toApplication()
        val profile =
            draft
                .toApplication(existing)
                .copy(
                    defaultModel = discoveredModel.id,
                    models = existing?.models?.mergeSelectedModel(selectedModel) ?: listOf(selectedModel),
                )
        val profiles = providerCatalog.listProviders().filterNot { it.id == profile.id } + profile
        providerCatalog
            .replaceConfigurationReactive(ApplicationProviderConfiguration(profiles, profile.id, discoveredModel.id))
            .awaitSingleOrNull()
    }

    override suspend fun createAgent(description: String): OnboardingAgentCreationResult = onboardingAgentService.createAgent(description)

    override fun complete() {
        preferenceStore.setPreference(ONBOARDING_KEY, OnboardingStatus.COMPLETED.name)
    }

    private fun OnboardingProviderDraft.toApplication(existing: ApplicationProviderProfile?): ApplicationProviderProfile {
        val credentialUpdate = credential
        val key =
            when (credentialUpdate) {
                CredentialUpdate.Unchanged -> existing?.apiKey.orEmpty()
                CredentialUpdate.Clear -> ""
                is CredentialUpdate.Replace -> credentialUpdate.value
            }
        require(id.isNotBlank() && name.isNotBlank()) { "Provider name and identifier are required." }
        require(adapter != ProviderAdapter.CODEX_CLI || baseUrl.isBlank()) { "Codex CLI does not use an HTTP endpoint." }
        require(adapter == ProviderAdapter.CODEX_CLI || baseUrl.isNotBlank()) { "An HTTP provider endpoint is required." }
        return ApplicationProviderProfile(
            id = id,
            name = name.trim(),
            adapter = ApplicationProviderAdapter.valueOf(adapter.name),
            baseUrl = baseUrl.trim(),
            apiKey = key,
            enabled = enabled,
            defaultModel = defaultModel,
            models = existing?.models.orEmpty(),
            options = existing?.options.orEmpty(),
            modelWhitelist = existing?.modelWhitelist.orEmpty(),
            modelBlacklist = existing?.modelBlacklist.orEmpty(),
        )
    }

    private fun ApplicationProviderProfile.toSafeView(): OnboardingProviderProfile =
        OnboardingProviderProfile(
            id = id,
            name = name,
            adapter = ProviderAdapter.valueOf(adapter.name),
            baseUrl = baseUrl,
            credentialConfigured = apiKey.isNotBlank(),
            enabled = enabled,
            defaultModel = defaultModel,
            models = models.map { model -> model.toProtocol() },
        )

    private fun ApplicationProviderModel.toProtocol(): ProviderModel =
        ProviderModel(
            id,
            name,
            de.heckenmann.visualagent.protocol.ModelStatus
                .valueOf(status.name),
            options,
            variants,
            contextLimit,
            outputLimit,
            capabilities,
        )

    private fun ProviderModel.toApplication(): ApplicationProviderModel =
        ApplicationProviderModel(
            id,
            name,
            de.heckenmann.visualagent.agent.provider.ModelStatus
                .valueOf(status.name),
            options,
            variants,
            contextLimit,
            outputLimit,
            capabilities,
        )

    private fun List<ApplicationProviderModel>.mergeSelectedModel(selected: ApplicationProviderModel): List<ApplicationProviderModel> {
        val existing = firstOrNull { it.id == selected.id } ?: return this + selected
        val merged =
            existing.copy(
                name = existing.name.takeUnless { it == existing.id } ?: selected.name,
                status = selected.status,
                contextLimit = existing.contextLimit ?: selected.contextLimit,
                outputLimit = existing.outputLimit ?: selected.outputLimit,
                capabilities = existing.capabilities.ifEmpty { selected.capabilities },
            )
        return map { model -> if (model.id == selected.id) merged else model }
    }

    private fun fingerprint(
        draft: OnboardingProviderDraft,
        modelId: String,
    ): String {
        val credentialMarker =
            when (val update = draft.credential) {
                CredentialUpdate.Unchanged -> "unchanged"
                CredentialUpdate.Clear -> "clear"
                is CredentialUpdate.Replace -> "replace:${update.value}"
            }
        val data =
            listOf(
                draft.id,
                draft.name,
                draft.adapter.name,
                draft.baseUrl,
                credentialMarker,
                draft.enabled,
                modelId,
            ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun Exception.toValidationCode(): OnboardingValidationCode =
        when {
            message.orEmpty().contains("required") || message.orEmpty().contains("does not use an HTTP endpoint") ->
                OnboardingValidationCode.INVALID_CONFIGURATION
            message.orEmpty().contains("401") || message.orEmpty().contains("403") -> OnboardingValidationCode.AUTHENTICATION_FAILED
            message.orEmpty().contains("Codex", ignoreCase = true) -> OnboardingValidationCode.PREREQUISITE_UNAVAILABLE
            else -> OnboardingValidationCode.ENDPOINT_UNAVAILABLE
        }

    private fun Exception.toSafeMessage(): String =
        when (toValidationCode()) {
            OnboardingValidationCode.INVALID_CONFIGURATION -> "The provider configuration is incomplete or invalid."
            OnboardingValidationCode.AUTHENTICATION_FAILED -> "Authentication was rejected by the provider."
            OnboardingValidationCode.PREREQUISITE_UNAVAILABLE -> "The provider prerequisite is unavailable on the Visual Agent server."
            else -> "The provider endpoint could not be reached from the Visual Agent server."
        }

    private companion object {
        const val ONBOARDING_KEY = "ui.onboarding.v1"
        const val ONBOARDING_VERSION = 1
        const val READINESS_MAX_TOKENS = 8
    }
}
