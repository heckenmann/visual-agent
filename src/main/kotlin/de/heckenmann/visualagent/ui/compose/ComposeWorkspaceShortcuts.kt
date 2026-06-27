package de.heckenmann.visualagent.ui.compose

/**
 * Stable panel order used by keyboard shortcuts and the left rail.
 */
internal val workspacePanelShortcutIds: List<String> =
    listOf("chat", "todos", "files", "agents", "settings", "canvas")

/**
 * Command metadata shown by the internal command palette.
 */
data class ComposeCommand(
    val id: String,
    val title: String,
    val description: String,
    val action: () -> Unit,
)

/**
 * Resolves a 1-based shortcut digit to the matching workspace panel ID.
 *
 * @param digit 1-based shortcut digit from `Cmd/Ctrl+1..6`
 * @return Workspace panel ID or `null` when the digit is not mapped
 */
internal fun panelIdForShortcutDigit(digit: Int): String? = workspacePanelShortcutIds.getOrNull(digit - 1)

/**
 * Filters command palette entries by title, description, or stable ID.
 *
 * @param commands Available commands
 * @param query User-entered search query
 * @return Matching commands, preserving original ordering
 */
internal fun filterCommands(
    commands: List<ComposeCommand>,
    query: String,
): List<ComposeCommand> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return commands
    return commands.filter { command ->
        command.id.contains(normalized, ignoreCase = true) ||
            command.title.contains(normalized, ignoreCase = true) ||
            command.description.contains(normalized, ignoreCase = true)
    }
}
