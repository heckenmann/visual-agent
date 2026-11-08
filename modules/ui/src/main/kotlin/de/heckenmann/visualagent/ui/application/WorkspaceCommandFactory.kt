package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.ui.workspace.ComposeCommand
import de.heckenmann.visualagent.ui.workspace.ComposeWorkspaceWindow

/** Creates command-palette actions for opening workspace panels and closing the application. */
internal fun workspaceCommands(
    windows: List<ComposeWorkspaceWindow>,
    activateWindow: (String) -> Unit,
    onCloseApplication: () -> Unit,
): List<ComposeCommand> =
    windows.map { window ->
        ComposeCommand(
            id = "open-${window.id}",
            title = "Open ${window.title}",
            description = window.subtitle,
        ) {
            activateWindow(window.id)
        }
    } + ComposeCommand("close-application", "Close application", "Close Visual Agent and persist workspace state", onCloseApplication)
