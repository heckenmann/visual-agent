package de.heckenmann.visualagent.agent.openai

import java.net.URI

/**
 * Normalizes OpenAI-compatible endpoint URLs for clients that expect the API base path.
 */
internal object OpenAiEndpointNormalizer {
    /**
     * Returns the configured endpoint with a `/v1` API path when no version path is present.
     *
     * @param configuredBaseUrl User-configured OpenAI-compatible endpoint
     * @return Normalized API base URL without a trailing slash
     */
    fun apiBaseUrl(configuredBaseUrl: String): String {
        val trimmed = configuredBaseUrl.trim().trimEnd('/')
        val baseUrl = trimmed.ifBlank { "https://api.openai.com" }
        val uri = URI.create(baseUrl)
        val path = uri.path.orEmpty().trimEnd('/')
        if (path == "/v1" || path.endsWith("/v1")) return baseUrl

        val normalizedPath = if (path.isBlank() || path == "/") "/v1" else "$path/v1"
        return URI(
            uri.scheme,
            uri.userInfo,
            uri.host,
            uri.port,
            normalizedPath,
            uri.query,
            uri.fragment,
        ).toString()
    }

    /**
     * Returns whether the endpoint is the official OpenAI API and therefore
     * must not be called with a placeholder API key.
     *
     * OpenAI-compatible local gateways are allowed to omit credentials because
     * some deployments intentionally run without authentication.
     */
    fun requiresApiKey(configuredBaseUrl: String): Boolean {
        val baseUrl = configuredBaseUrl.trim().ifBlank { "https://api.openai.com" }
        val host =
            URI
                .create(baseUrl)
                .host
                .orEmpty()
                .lowercase()
        return host == "api.openai.com"
    }
}
