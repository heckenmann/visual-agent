package de.heckenmann.visualagent.agent.provider

/** Persists credentials for only the explicitly selected route, without inferring or switching billing. */
internal fun ProviderProfile.withSelectedCredentialRoute(): ProviderProfile =
    when {
        adapter != ProviderAdapter.CODEX_DIRECT -> copy(directAuthMode = DirectProviderAuthMode.API_KEY, credentialReference = null)
        directAuthMode == DirectProviderAuthMode.API_KEY -> copy(credentialReference = null)
        else -> copy(apiKey = "")
    }
