package de.heckenmann.visualagent.ui.compose

import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.error.UserFacingError

/**
 * Describes an internal modal rendered inside the main Compose window.
 */
sealed interface ComposeModal

/**
 * Describes an internal confirmation modal rendered inside the main Compose window.
 */
data class ComposeConfirmationModal(
    val title: String,
    val message: String,
    val confirmDescription: String,
    val dismissDescription: String = "Cancel",
    val onConfirm: () -> Unit,
) : ComposeModal

/**
 * Describes a read-only information modal rendered inside the main Compose window.
 */
data class ComposeInfoModal(
    val title: String,
    val message: String,
    val dismissDescription: String = "Close",
) : ComposeModal

/**
 * Describes a modal that renders custom Compose content inside the main window.
 */
data class ComposeContentModal(
    val title: String,
    val content: @Composable (dismiss: () -> Unit) -> Unit,
) : ComposeModal

/**
 * Describes an error modal that presents a structured [UserFacingError] with optional recovery
 * actions.
 *
 * @param userError Structured error description to display
 * @param onDismiss Called when the user closes the modal
 * @param onRetry Called when the user chooses to retry the failed operation; only shown when
 *   [UserFacingError.retryable] is true
 * @param onCopyDetails Called when the user requests to copy error details to the clipboard; shown
 *   when non-null
 * @param dismissDescription Accessibility label for the dismiss button
 */
internal data class ComposeErrorModal(
    val userError: UserFacingError,
    val onDismiss: () -> Unit,
    val onRetry: (() -> Unit)? = null,
    val onCopyDetails: (() -> Unit)? = null,
    val dismissDescription: String = "Close",
) : ComposeModal

/**
 * Request API passed to panels that need internal modal interactions.
 */
fun interface ComposeModalRequester {
    /**
     * Shows a modal and prevents interacting with workspace windows until it is resolved.
     *
     * @param modal Modal to render
     */
    fun request(modal: ComposeModal)
}

/**
 * Shows a confirmation modal and prevents interacting with workspace windows until it is resolved.
 *
 * @param modal Confirmation modal to render
 */
fun ComposeModalRequester.requestConfirmation(modal: ComposeConfirmationModal) = request(modal)

/**
 * Shows a read-only information modal and prevents interacting with workspace windows until it is closed.
 *
 * @param modal Information modal to render
 */
fun ComposeModalRequester.requestInfo(modal: ComposeInfoModal) = request(modal)

/**
 * Shows an error modal and prevents interacting with workspace windows until it is dismissed.
 *
 * @param modal Error modal to render
 */
internal fun ComposeModalRequester.requestError(modal: ComposeErrorModal) = request(modal)
