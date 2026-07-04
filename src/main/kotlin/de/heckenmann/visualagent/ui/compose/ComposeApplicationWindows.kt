@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

internal fun defaultWindows(): List<ComposeWorkspaceWindow> =
    workspacePanelShortcutIds.map { id ->
        when (id) {
            "chat" ->
                ComposeWorkspaceWindow(
                    id = "chat",
                    icon = "C",
                    title = "Conversation",
                    subtitle = "Main agent conversation and history",
                    bounds = ComposeWorkspaceWindowBounds(x = 24, y = 92, width = 520, height = 460),
                )
            "todos" ->
                ComposeWorkspaceWindow(
                    id = "todos",
                    icon = "T",
                    title = "Todos",
                    subtitle = "DB-backed task management",
                    bounds = ComposeWorkspaceWindowBounds(x = 570, y = 92, width = 420, height = 420),
                )
            "files" ->
                ComposeWorkspaceWindow(
                    id = "files",
                    icon = "F",
                    title = "Files",
                    subtitle = "Workspace import, sync, rename, delete",
                    bounds = ComposeWorkspaceWindowBounds(x = 120, y = 570, width = 540, height = 340),
                    visible = false,
                )
            "agents" ->
                ComposeWorkspaceWindow(
                    id = "agents",
                    icon = "A",
                    title = "Subagents",
                    subtitle = "Worker creation and live job counts",
                    bounds = ComposeWorkspaceWindowBounds(x = 690, y = 540, width = 460, height = 350),
                    visible = false,
                )
            "settings" ->
                ComposeWorkspaceWindow(
                    id = "settings",
                    icon = "S",
                    title = "Settings",
                    subtitle = "Provider, model, theme, font size",
                    bounds = ComposeWorkspaceWindowBounds(x = 760, y = 140, width = 420, height = 360),
                    visible = false,
                )
            "canvas" ->
                ComposeWorkspaceWindow(
                    id = "canvas",
                    icon = "D",
                    title = "Canvas",
                    subtitle = "Structured drawing and capture",
                    bounds = ComposeWorkspaceWindowBounds(x = 260, y = 170, width = 620, height = 460),
                    visible = false,
                )
            else -> error("Unsupported workspace panel id: $id")
        }
    }
