package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.settings.forms.ConversationSettingsPage

/** Opens only model context and message-composition preferences from the Conversation header. */
internal fun openConversationProviderSettings(
    modalRequester: ComposeModalRequester,
    settingsPort: SettingsPort,
    providerPort: ProviderPort,
    onSettingsChanged: () -> Unit,
    onManageProviders: () -> Unit = {},
) {
    modalRequester.request(
        ComposeContentModal(title = "Conversation settings") { dismiss ->
            ConversationSettingsPage(settingsPort, providerPort, onSettingsChanged) {
                dismiss()
                onManageProviders()
            }
        },
    )
}
