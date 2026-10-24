package de.heckenmann.visualagent.ui.conversation.models

import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.ui.application.ComposePanelServices
import de.heckenmann.visualagent.ui.conversation.ConversationPanel
import de.heckenmann.visualagent.ui.settings.navigation.SettingsDestination

/** Owns model state above either lazy or pinned composer so scrolling cannot reload the catalog. */
@Composable
internal fun ConversationWorkspacePanel(services: ComposePanelServices) {
    val models = rememberConversationModelState(services.providers, services.settings)
    ConversationPanel(
        modalRequester = services.modalRequester,
        inFlight = services.inFlight,
        activityPort = services.activity,
        todoPort = services.todos,
        conversationPort = services.conversation,
        suggestionPort = services.conversationSuggestions,
        settingsPort = services.settings,
        clientImagePort = services.clientImagePort,
        modelSelector = { ConversationModelPicker(models) { services.onOpenSettings(SettingsDestination.PROVIDERS) } },
    )
}
