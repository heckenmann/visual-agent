package de.heckenmann.visualagent.agent.provider

/** Enforces the same documented endpoint contract for authorization, catalog discovery, and inference. */
fun ProviderProfile.requireDirectCodexEndpoint() {
    require(adapter == ProviderAdapter.CODEX_DIRECT) { "The profile is not a direct Codex profile" }
    require(baseUrl.trimEnd('/') in setOf("https://api.openai.com", "https://api.openai.com/v1")) {
        "Direct Codex requires the documented OpenAI API endpoint"
    }
}

/** Persists credentials for only the explicitly selected route, without inferring or switching billing. */
internal fun ProviderProfile.withSelectedCredentialRoute(): ProviderProfile =
    when {
        adapter != ProviderAdapter.CODEX_DIRECT -> copy(directAuthMode = DirectProviderAuthMode.API_KEY, credentialReference = null)
        directAuthMode == DirectProviderAuthMode.API_KEY -> copy(credentialReference = null)
        else -> copy(apiKey = "")
    }
