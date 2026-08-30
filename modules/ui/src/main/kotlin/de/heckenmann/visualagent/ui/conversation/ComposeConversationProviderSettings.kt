package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.modal.ComposeSettingsModal
import de.heckenmann.visualagent.ui.modal.requestSettings
import de.heckenmann.visualagent.ui.settings.providerSettingsOverlay

/** Opens the reusable global provider and model settings overlay from the conversation header. */
internal fun openConversationProviderSettings(
    modalRequester: ComposeModalRequester,
    settingsPort: SettingsPort,
    providerPort: ProviderPort,
    onSettingsChanged: () -> Unit,
) {
    modalRequester.requestSettings(
        ComposeSettingsModal(title = "Providers and models") {
            providerSettingsOverlay(
                settingsPort = settingsPort,
                providerPort = providerPort,
                onSettingsChanged = onSettingsChanged,
            )
        },
    )
}
