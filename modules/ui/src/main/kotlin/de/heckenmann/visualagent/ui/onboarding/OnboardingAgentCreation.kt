package de.heckenmann.visualagent.ui.onboarding

import de.heckenmann.visualagent.protocol.CredentialUpdate
import de.heckenmann.visualagent.protocol.DirectProviderAuthMode
import de.heckenmann.visualagent.protocol.OnboardingAgent
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationAvailability
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationResult
import de.heckenmann.visualagent.protocol.OnboardingPort
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingProviderProfile
import de.heckenmann.visualagent.protocol.ProviderModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resolves credential presence without exposing or copying a credential value. */
internal fun onboardingCredentialConfigured(
    draft: OnboardingProviderDraft?,
    selected: OnboardingProviderProfile?,
): Boolean =
    when (val credential = draft?.credential) {
        CredentialUpdate.Unchanged ->
            if (draft.directAuthMode == DirectProviderAuthMode.CHATGPT_OAUTH) {
                draft.credentialReference != null
            } else {
                selected?.credentialConfigured == true
            }
        CredentialUpdate.Clear -> false
        is CredentialUpdate.Replace -> credential.value.isNotBlank()
        null -> false
    }

/** Validates and saves the staged provider before loading the optional agent step. */
internal fun saveOnboardingProvider(
    scope: CoroutineScope,
    onboarding: OnboardingPort,
    draft: OnboardingProviderDraft?,
    model: ProviderModel?,
    onSaving: (Boolean) -> Unit,
    onValidationFailed: (String) -> Unit,
    onSaved: (List<OnboardingAgent>, OnboardingAgentCreationAvailability) -> Unit,
    onFailure: () -> Unit,
) {
    scope.launch {
        onSaving(true)
        try {
            val providerDraft = checkNotNull(draft)
            val selectedModel = checkNotNull(model)
            val validation = withContext(Dispatchers.IO) { onboarding.validate(providerDraft, selectedModel.id) }
            if (!validation.success) {
                onValidationFailed(validation.message)
            } else {
                withContext(Dispatchers.IO) {
                    onboarding.finish(providerDraft, selectedModel, checkNotNull(validation.validationFingerprint))
                }
                val (agents, availability) = loadOnboardingAgentStep(onboarding)
                onSaved(agents, availability)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onFailure()
        } finally {
            onSaving(false)
        }
    }
}

private suspend fun loadOnboardingAgentStep(onboarding: OnboardingPort): Pair<List<OnboardingAgent>, OnboardingAgentCreationAvailability> =
    try {
        withContext(Dispatchers.IO) { onboarding.agents() to onboarding.agentCreationAvailability() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList<OnboardingAgent>() to
            OnboardingAgentCreationAvailability(
                available = false,
                reason = "Your provider and model are saved, but the sub-agent setup could not be loaded. You can skip this step.",
            )
    }

/** Discovers staged provider models without blocking Compose or converting cancellation to an error. */
internal fun discoverOnboardingModels(
    scope: CoroutineScope,
    onboarding: OnboardingPort,
    draft: OnboardingProviderDraft?,
    onLoading: (Boolean) -> Unit,
    onDiscovered: (List<ProviderModel>) -> Unit,
    onFailure: () -> Unit,
) {
    val providerDraft = draft ?: return
    scope.launch {
        onLoading(true)
        try {
            onDiscovered(withContext(Dispatchers.IO) { onboarding.discoverModels(providerDraft) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onFailure()
        } finally {
            onLoading(false)
        }
    }
}

/** Runs the onboarding request on the server dispatcher and publishes its outcome on the UI scope. */
internal fun createOnboardingAgent(
    scope: CoroutineScope,
    onboarding: OnboardingPort,
    description: String,
    onCreating: (Boolean) -> Unit,
    onCreated: (OnboardingAgentCreationResult) -> Unit,
    onFailure: () -> Unit,
) {
    scope.launch {
        onCreating(true)
        try {
            onCreated(withContext(Dispatchers.IO) { onboarding.createAgent(description) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onFailure()
        } finally {
            onCreating(false)
        }
    }
}
