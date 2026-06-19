package de.heckenmann.visualagent.agent.provider

/**
 * Converts provider exceptions into concise, actionable messages safe for the UI.
 */
internal object ProviderErrorMessages {
    fun userFacing(error: Throwable): String {
        val detail = generateSequence(error) { it.cause }.mapNotNull(Throwable::message).joinToString(" ").lowercase()
        return when {
            "429" in detail || "quota" in detail || "rate limit" in detail ->
                "The provider rejected the request because no quota is currently available. Check billing or choose another provider."
            "403" in detail || "subscription" in detail || "upgrade for access" in detail ->
                "The selected model is not available for this account. Choose another model or update the provider subscription."
            "401" in detail || "unauthorized" in detail || "api key" in detail ->
                "Authentication failed. Check the provider API key and base URL in Session settings."
            "timeout" in detail || "timed out" in detail ->
                "The provider did not respond before the request timeout. Try again or increase the timeout in Session settings."
            "connection refused" in detail || "unknown host" in detail || "dns" in detail ->
                "The provider could not be reached. Check the connection and provider base URL."
            else ->
                "The model request failed. Check the active provider and model, then try again."
        }
    }
}
