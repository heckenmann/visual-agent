package de.heckenmann.visualagent.error

/**
 * Base exception for errors that should be shown to the user in a structured way.
 *
 * Carries enough metadata so that [ErrorMessageMapper] can produce a [UserFacingError] without
 * inspecting raw exception messages.
 *
 * @param category Error category used for UI styling and actions
 * @param summary Short title of what went wrong
 * @param detail Actionable hint for the user
 * @param retryable Whether the operation can reasonably be retried
 * @param cause Optional underlying exception (must never contain credentials or user PII)
 */
internal open class VisualAgentException(
    val category: ErrorCategory,
    val summary: String,
    val detail: String,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : RuntimeException("$summary: $detail", cause)

/**
 * Workspace file error (missing file, import failure, rename failure, etc.).
 *
 * @param summary Short title of what went wrong
 * @param detail Actionable hint for the user
 * @param retryable Whether the operation can reasonably be retried
 * @param cause Optional underlying exception
 */
internal class WorkspaceFileException(
    summary: String,
    detail: String,
    retryable: Boolean = false,
    cause: Throwable? = null,
) : VisualAgentException(ErrorCategory.WORKSPACE, summary, detail, retryable, cause)

/**
 * Canvas operation error (import, export, figure manipulation, document codec failure).
 *
 * @param summary Short title of what went wrong
 * @param detail Actionable hint for the user
 * @param retryable Whether the operation can reasonably be retried
 * @param cause Optional underlying exception
 */
internal class CanvasOperationException(
    summary: String,
    detail: String,
    retryable: Boolean = false,
    cause: Throwable? = null,
) : VisualAgentException(ErrorCategory.CANVAS, summary, detail, retryable, cause)

/**
 * Tool execution or tool-input validation error.
 *
 * @param summary Short title of what went wrong
 * @param detail Actionable hint for the user
 * @param retryable Whether the operation can reasonably be retried
 * @param cause Optional underlying exception
 */
internal class ToolExecutionException(
    summary: String,
    detail: String,
    retryable: Boolean = false,
    cause: Throwable? = null,
) : VisualAgentException(ErrorCategory.TOOL, summary, detail, retryable, cause)

/**
 * Persistence layer error (database, repository, store failure).
 *
 * @param summary Short title of what went wrong
 * @param detail Actionable hint for the user
 * @param retryable Whether the operation can reasonably be retried
 * @param cause Optional underlying exception
 */
internal class PersistenceException(
    summary: String,
    detail: String,
    retryable: Boolean = false,
    cause: Throwable? = null,
) : VisualAgentException(ErrorCategory.PERSISTENCE, summary, detail, retryable, cause)
