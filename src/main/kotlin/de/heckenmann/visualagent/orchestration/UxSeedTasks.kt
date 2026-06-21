package de.heckenmann.visualagent.orchestration

/**
 * Canonical autonomous UX seed tasks for the Visual Agent backlog.
 *
 * Use cases: UC-0000053.
 */
internal object UxSeedTasks {
    /**
     * Returns all default UX tasks used by autonomous seeding.
     *
     * @return Ordered list of seed task descriptions
     */
    fun all(): List<String> =
        listOf(
            "ChatPanel: implement message grouping visual polish",
            "ChatPanel: implement typing indicator & streaming partial responses",
            "ChatPanel: add message actions (retry, edit, delete, pin, reactions)",
            "ChatPanel: virtualize message list for large histories",
            "ChatPanel: accessibility pass (focus, labels, contrast)",
            "MainWindow: persistent left navigation with active state and tooltips",
            "MainWindow: command palette (Cmd/Ctrl+K) to switch panels/search",
            "StatusBar: actionable controls (Retry, Reconnect)",
            "SessionPanel: model search, filter, favorites and quick actions",
            "SessionPanel: improve loading/error states for model info",
            "TodoPanel: inline create/edit without modal",
            "TodoPanel: undo snackbar for deletes",
            "TodoPanel: bulk actions and filters",
            "SubAgentsPanel: agent cards with quick run/stop and logs preview",
            "SubAgentsPanel: agent creation wizard with templates",
            "CanvasPanel: toolbar (pen, eraser, undo/redo, export)",
            "CanvasPanel: pan/zoom, grid, snapshots saving",
            "ApplicationSettings: group settings, live theme preview, import/export config",
            "Cross-cutting: component library mapping and CSS tokenization",
            "Cross-cutting: accessibility pass and contrast audit",
        )
}
