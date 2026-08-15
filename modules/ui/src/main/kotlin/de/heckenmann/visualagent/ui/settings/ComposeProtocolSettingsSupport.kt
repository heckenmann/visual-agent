package de.heckenmann.visualagent.ui.settings

import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester

/** Snapshot of provider data needed by the settings presentation. */
internal data class ProviderSettingsState(
    val providers: List<ProviderProfile>,
    val providerId: String,
    val modelId: String,
    val models: List<ProviderModel>,
)

/** Loads provider profiles and a valid active model through the protocol port. */
internal fun readProviderSettings(
    providerPort: ProviderPort,
    preferredProviderId: String,
): ProviderSettingsState {
    val providers = providerPort.listProviders()
    val enabled = providers.filter(ProviderProfile::enabled)
    val providerId = preferredProviderId.takeIf { id -> enabled.any { it.id == id } } ?: enabled.firstOrNull()?.id.orEmpty()
    val models = providerPort.selectableModels(providerId)
    val modelId = providerPort.activeModelId().takeIf { it in models.map(ProviderModel::id) } ?: models.firstOrNull()?.id.orEmpty()
    return ProviderSettingsState(providers, providerId, modelId, models)
}

/** Returns the supported queue delivery modes. */
internal fun queueFlushOptions(): List<PanelSelectOption> =
    listOf(PanelSelectOption("ONE_BY_ONE", "One by one"), PanelSelectOption("ALL_AT_ONCE", "All at once"))

/** Opens the protocol-only provider profile editor in the shared modal host. */
internal fun ComposeModalRequester.requestProviderProfileDialog(
    profile: ProviderProfile?,
    canDisable: Boolean,
    onSave: (ProviderProfile) -> Unit,
) {
    request(
        ComposeContentModal(title = if (profile == null) "New provider" else "Edit provider") { dismiss ->
            ProviderProfileEditor(
                initial = profile?.toFormState() ?: newProviderFormState(),
                existing = profile,
                canDisable = canDisable,
                onCancel = dismiss,
                onSave = { saved ->
                    onSave(saved)
                    dismiss()
                },
            )
        },
    )
}
