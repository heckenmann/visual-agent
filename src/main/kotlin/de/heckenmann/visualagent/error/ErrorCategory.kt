package de.heckenmann.visualagent.error

/**
 * Broad category of a user-facing error. The UI can use the category to choose the right icon,
 * color, and default actions (for example "Open Settings" for provider-auth errors or "Retry" for
 * transient workspace errors).
 */
internal enum class ErrorCategory {
    /**
     * Errors that originate from an LLM provider or network call to a provider.
     */
    PROVIDER,

    /**
     * Errors related to workspace files (import, read, rename, delete, missing file).
     */
    WORKSPACE,

    /**
     * Errors related to the canvas (import, export, figure manipulation, document codec).
     */
    CANVAS,

    /**
     * Errors caused by invalid tool input or tool execution failures.
     */
    TOOL,

    /**
     * Errors from the persistence layer (database, repositories, stores).
     */
    PERSISTENCE,

    /**
     * Errors that do not match any known category.
     */
    UNKNOWN,
}
