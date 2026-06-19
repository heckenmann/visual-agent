package de.heckenmann.visualagent.ui

import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.layout.Pane

internal fun createMainWorkspaceWindows(
    desktop: Pane,
    panels: MainWorkspacePanels,
): WorkspaceWindowManager =
    WorkspaceWindowManager(desktop).apply {
        register("conversation", "Conversation", "fas-comments", panels.chatPanel, WindowPlacement(18.0, 18.0, 650.0, 500.0))
        register("session", "Session", "fas-bars", panels.sessionPanel, WindowPlacement(700.0, 18.0, 430.0, 460.0))
        register("agents", "Agents", "fas-users", panels.subAgentsPanel, WindowPlacement(700.0, 500.0, 430.0, 330.0))
        register("todos", "Todos", "fas-copy", panels.todoPanel, WindowPlacement(60.0, 540.0, 520.0, 300.0))
        register("canvas", "Canvas", "fas-palette", panels.canvasPanel, WindowPlacement(220.0, 130.0, 720.0, 560.0))
        register("settings", "Settings", "fas-cog", panels.settingsPanel, WindowPlacement(740.0, 120.0, 420.0, 430.0))
    }

internal data class MainWorkspacePanels(
    val chatPanel: Node,
    val sessionPanel: Node,
    val subAgentsPanel: Node,
    val todoPanel: Node,
    val canvasPanel: Node,
    val settingsPanel: Node,
)

internal fun wireMainWorkspaceNavigation(
    buttons: MainWorkspaceButtons,
    panels: MainWorkspacePanels,
    focusPanel: (Node, Button) -> Unit,
): LinkedHashMap<Button, Node> {
    val mapping = LinkedHashMap<Button, Node>()
    mapping[buttons.conversationBtn] = panels.chatPanel
    mapping[buttons.sessionBtn] = panels.sessionPanel
    mapping[buttons.agentsBtn] = panels.subAgentsPanel
    mapping[buttons.planBtn] = panels.todoPanel
    mapping[buttons.canvasBtn] = panels.canvasPanel
    mapping[buttons.settingsBtn] = panels.settingsPanel
    mapping.forEach { (button, panel) -> button.setOnAction { focusPanel(panel, button) } }
    return mapping
}

internal data class MainWorkspaceButtons(
    val conversationBtn: Button,
    val sessionBtn: Button,
    val agentsBtn: Button,
    val planBtn: Button,
    val canvasBtn: Button,
    val settingsBtn: Button,
)
