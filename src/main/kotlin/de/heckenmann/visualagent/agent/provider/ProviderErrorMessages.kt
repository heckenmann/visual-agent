package de.heckenmann.visualagent.agent.provider

import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.error.UserFacingError

/**
 * Converts provider exceptions into concise, actionable messages safe for the UI.
 */
internal object ProviderErrorMessages {
    /**
     * Returns a user-facing string for the given provider error.
     *
     * @param error Original exception from a provider call
     * @return Actionable, human-readable error text
     */
    fun userFacing(error: Throwable): String {
        val userError = ErrorMessageMapper.map(error)
        return "${userError.summary}: ${userError.detail}"
    }

    /**
     * Returns a structured [UserFacingError] for the given provider error.
     *
     * @param error Original exception from a provider call
     * @return Structured error description, including retryability
     */
    fun userFacingError(error: Throwable): UserFacingError = ErrorMessageMapper.map(error)
}
