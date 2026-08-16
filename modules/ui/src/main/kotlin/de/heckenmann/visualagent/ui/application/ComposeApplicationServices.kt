package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.ClientImagePort
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import de.heckenmann.visualagent.ui.workspace.ComposeWorkspaceWindow
import de.heckenmann.visualagent.ui.workspace.ComposeWorkspaceWindowBounds

/** Restores persisted workspace visibility, order, and preferred widths. */
fun restoreWorkspaceWindows(
    defaults: List<ComposeWorkspaceWindow>,
    persisted: List<LayoutWindowState>,
): List<ComposeWorkspaceWindow> {
    if (persisted.isEmpty()) return defaults
    val persistedById = persisted.associateBy { it.id }
    return defaults
        .mapIndexed { defaultIndex, window ->
            val persistedState = persistedById[window.id]
            val width =
                persistedState
                    ?.preferredWidth
                    ?.takeIf { it > 0 }
                    ?.toInt()
                    ?.coerceAtLeast(ComposeWorkspaceWindowBounds.MIN_WIDTH) ?: window.preferredWidth
            window.copy(
                visible = persistedState?.visible ?: window.visible,
                preferredWidth = width,
            ) to (persistedState?.order ?: (Int.MAX_VALUE - defaults.size + defaultIndex))
        }.sortedWith(compareBy({ it.second }, { defaults.indexOf(it.first) }))
        .map { it.first }
}

/** Bundles only transport ports and Compose-owned presentation state. */
data class ComposePanelServices(
    val settings: de.heckenmann.visualagent.protocol.SettingsPort,
    val agents: de.heckenmann.visualagent.protocol.AgentPort,
    val providers: de.heckenmann.visualagent.protocol.ProviderPort,
    val activity: de.heckenmann.visualagent.protocol.ActivityPort,
    val workspaceFiles: de.heckenmann.visualagent.protocol.WorkspaceFilePort,
    val canvas: de.heckenmann.visualagent.protocol.CanvasPort,
    val conversation: de.heckenmann.visualagent.protocol.ConversationPort,
    val clientImagePort: ClientImagePort,
    val todos: de.heckenmann.visualagent.protocol.TodoPort,
    val modalRequester: ComposeModalRequester,
    val onSettingsChanged: () -> Unit,
    val inFlight: InFlightStateHolder,
    val lifecycle: de.heckenmann.visualagent.protocol.LifecyclePort,
)
