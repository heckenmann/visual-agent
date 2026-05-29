package de.heckenmann.visualagent.ui

/**
 * Optional interface for panels that support a global back action.
 * Implementing panels should expose an onBack callback; MainWindow will
 * display the global back button when the active panel implements this.
 */
interface BackNavigable {
    var onBack: (() -> Unit)?
    val showsBackButton: Boolean
}
