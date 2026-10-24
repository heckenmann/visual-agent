package de.heckenmann.visualagent.ui.settings

import de.heckenmann.visualagent.protocol.DirectProviderAuthMode
import de.heckenmann.visualagent.protocol.ProviderAdapter

/** Selection intent retained while a provider's current model catalog is refreshed. */
internal data class ProviderDraftSelection(
    val modelId: String,
    val preferredModelId: String?,
)

/** Resolves persisted/default selection intent before an asynchronous provider refresh. */
internal fun ProviderSettingsDraft.selectionForProvider(
    persisted: ProviderSettingsDraft,
    id: String,
): ProviderDraftSelection {
    val provider = providers.firstOrNull { it.id == id }
    val preferred = if (id == persisted.providerId) persisted.modelId else provider?.defaultModel
    val model =
        preferred?.takeIf(String::isNotBlank) ?: provider
            ?.selectableModels()
            ?.firstOrNull()
            ?.id
            .orEmpty()
    return ProviderDraftSelection(model, preferred)
}

/** Applies a safe authorization reference without overwriting settings edited during browser sign-in. */
internal fun ProviderSettingsDraft.withDirectCredentialReference(
    providerId: String,
    reference: String?,
): ProviderSettingsDraft =
    copy(
        providers =
            providers.map { profile ->
                if (profile.id == providerId &&
                    profile.adapter == ProviderAdapter.CODEX_DIRECT &&
                    profile.directAuthMode == DirectProviderAuthMode.CHATGPT_OAUTH
                ) {
                    profile.copy(credentialReference = reference, apiKey = "")
                } else {
                    profile
                }
            },
    )
