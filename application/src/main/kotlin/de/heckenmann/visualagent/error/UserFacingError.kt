package de.heckenmann.visualagent.error

/**
 * User-facing error description for UI display.
 *
 * @param category Broad error category used by the UI to pick styling and actions
 * @param summary Short title of what went wrong (e.g. "Model not available")
 * @param detail Actionable hint describing what the user can do next
 * @param retryable Whether the operation can reasonably be retried
 */
internal data class UserFacingError(
    val category: ErrorCategory,
    val summary: String,
    val detail: String,
    val retryable: Boolean = false,
)
