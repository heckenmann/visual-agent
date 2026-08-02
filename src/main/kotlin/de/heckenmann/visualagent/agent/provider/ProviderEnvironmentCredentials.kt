package de.heckenmann.visualagent.agent.provider

/**
 * Resolves provider credentials from environment variables without persisting or exposing them.
 */
internal object ProviderEnvironmentCredentials {
    /**
     * Returns the API key for an OpenAI-compatible profile.
     *
     * Explicit profile credentials take precedence over the standard OpenAI variable.
     *
     * @param profile Provider profile whose credentials should be resolved
     * @return API key or an empty string when none is configured
     */
    fun openAiApiKey(profile: ProviderProfile): String {
        if (profile.apiKey.isNotBlank()) return profile.apiKey
        return System.getenv("OPENAI_API_KEY")?.takeIf(String::isNotBlank).orEmpty()
    }

    /**
     * Stable ID of the built-in ChatGPT Codex provider profile.
     */
    const val CODEX_PROFILE_ID = "openai-codex"
}
