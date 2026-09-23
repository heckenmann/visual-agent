package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.ui.workspace.ComposeWorkspaceWindow

/** Converts one Compose workspace window into its protocol layout representation. */
internal fun ComposeWorkspaceWindow.toLayoutWindowState(order: Int): LayoutWindowState =
    LayoutWindowState(
        id = id,
        order = order,
        visible = visible,
        preferredWidth = preferredWidth.toDouble(),
    )
