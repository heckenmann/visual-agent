package de.heckenmann.visualagent.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelInfoBox

/** Discards only legacy whole-list overrides after an explicit user action. */
internal fun ProviderSettingsDraft.resetLegacyCapabilities(providerId: String): ProviderSettingsDraft =
    copy(
        providers =
            providers.map { provider ->
                if (provider.id != providerId) {
                    provider
                } else {
                    provider.copy(
                        models =
                            provider.models.map { model ->
                                if (!model.capabilitiesOverridden) {
                                    model
                                } else {
                                    model.copy(
                                        capabilities = emptySet(),
                                        capabilitiesComplete = false,
                                        capabilitiesOverridden = false,
                                    )
                                }
                            },
                    )
                }
            },
    )

/** Resets legacy declarations without selecting every affected model or changing the database manually. */
@Composable
internal fun LegacyCapabilityReset(
    draft: ProviderSettingsDraft,
    onReset: () -> Unit,
) {
    if (draft.providers
            .firstOrNull { it.id == draft.providerId }
            ?.models
            ?.any { it.capabilitiesOverridden } != true
    ) {
        return
    }
    PanelInfoBox(
        "This provider has legacy whole-list capability overrides. Reset them to unknown, then refresh models to reload provider evidence. Targeted overrides are retained.",
    )
    ActionIconButton(Icons.Filled.Restore, "Reset legacy capability overrides for this provider", onReset)
}
