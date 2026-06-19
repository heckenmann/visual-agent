package de.heckenmann.visualagent.ui

internal fun shouldShowBack(
    activePanel: Any,
    chatPanel: Any,
): Boolean = activePanel !== chatPanel
