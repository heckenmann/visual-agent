package de.heckenmann.visualagent.ui.compose

/**
 * Describes an internal confirmation modal rendered inside the main Compose window.
 */
data class ComposeConfirmationModal(
    val title: String,
    val message: String,
    val confirmDescription: String,
    val dismissDescription: String = "Cancel",
    val onConfirm: () -> Unit,
)

/**
 * Request API passed to panels that need internal modal confirmation.
 */
fun interface ComposeModalRequester {
    /**
     * Shows a confirmation modal and prevents interacting with workspace windows until it is resolved.
     *
     * @param modal Confirmation modal to render
     */
    fun requestConfirmation(modal: ComposeConfirmationModal)
}
