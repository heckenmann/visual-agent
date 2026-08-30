@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*

/**
 * Selects and renders the body for a workspace panel.
 *
 * @param window Panel descriptor
 * @param panelServices Services required by the panel body
 */
@Composable
internal fun WindowBody(
    window: ComposeWorkspaceWindow,
    panelServices: ComposePanelServices,
) {
    when (window.id) {
        "chat" ->
            ConversationPanel(
                modalRequester = panelServices.modalRequester,
                inFlight = panelServices.inFlight,
                activityPort = panelServices.activity,
                todoPort = panelServices.todos,
                conversationPort = panelServices.conversation,
                clientImagePort = panelServices.clientImagePort,
            )
        "todos" ->
            TodoPanel(
                todoPort = panelServices.todos,
                modalRequester = panelServices.modalRequester,
                lifecycle = panelServices.lifecycle,
            )
        "files" ->
            FilesPanel(
                workspaceFileService = panelServices.workspaceFiles,
                canvasOperations = panelServices.canvas,
                modalRequester = panelServices.modalRequester,
                activityPort = panelServices.activity,
            )
        "agents" ->
            SubAgentsPanel(
                agentPort = panelServices.agents,
                providerPort = panelServices.providers,
                modalRequester = panelServices.modalRequester,
                activityPort = panelServices.activity,
                todoPort = panelServices.todos,
            )
        "settings" ->
            settingsPanel(
                settingsPort = panelServices.settings,
                onSettingsChanged = panelServices.onSettingsChanged,
            )
        "canvas" ->
            CanvasPanel(
                canvasOperations = panelServices.canvas,
                workspaceFileService = panelServices.workspaceFiles,
                modalRequester = panelServices.modalRequester,
                activityPort = panelServices.activity,
            )
    }
}
