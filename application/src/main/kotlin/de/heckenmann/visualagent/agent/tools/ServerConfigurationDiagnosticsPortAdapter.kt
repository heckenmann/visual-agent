package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.api.EffectiveServerConfigurationDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ProviderConfigurationDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerConfigurationDiagnosticsPort
import de.heckenmann.visualagent.config.AppConfigBean
import org.springframework.stereotype.Component
import java.net.URI

/** Reads the active database-backed provider configuration and safe runtime settings. */
@Component
class ServerConfigurationDiagnosticsPortAdapter(
    private val providerCatalog: ProviderCatalogService,
    private val appConfig: AppConfigBean,
) : ServerConfigurationDiagnosticsPort {
    override fun snapshot(): EffectiveServerConfigurationDiagnostic {
        val providerId = providerCatalog.activeProviderId()
        val modelId = providerCatalog.activeModelId()
        val profile = providerCatalog.getProvider(providerId)
        val model = profile?.models?.firstOrNull { it.id == modelId }
        val warnings =
            buildList {
                if (profile == null) {
                    add("The active provider profile is missing.")
                } else {
                    if (!profile.enabled) add("The active provider profile is disabled.")
                    if (model == null) add("The active model is not present in the provider catalog.")
                    if (model != null && providerCatalog.selectableModels(providerId).none { it.id == modelId }) {
                        add("The active model is disabled or filtered by provider selection rules.")
                    }
                }
                if (appConfig.contextLength <= 0) add("The configured context window must be positive.")
                if (appConfig.timeoutSeconds <= 0) add("The configured timeout must be positive.")
                if (appConfig.maxParallelSubAgents <= 0) add("The maximum parallel sub-agent count must be positive.")
            }
        val warningCodes =
            buildList {
                if (profile == null) {
                    add("active_provider_profile_missing")
                } else {
                    if (!profile.enabled) add("active_provider_profile_disabled")
                    if (model == null) {
                        add("active_model_missing")
                    } else if (providerCatalog.selectableModels(providerId).none { it.id == modelId }) {
                        add("active_model_not_selectable")
                    }
                }
                if (appConfig.contextLength <= 0) add("context_window_non_positive")
                if (appConfig.timeoutSeconds <= 0) add("timeout_non_positive")
                if (appConfig.maxParallelSubAgents <= 0) add("max_parallel_sub_agents_non_positive")
            }
        return EffectiveServerConfigurationDiagnostic(
            provider =
                ProviderConfigurationDiagnostic(
                    providerId = providerId,
                    adapter = profile?.adapter?.name,
                    enabled = profile?.enabled ?: false,
                    endpointOrigin = profile?.baseUrl?.let(::diagnosticEndpointOrigin),
                    apiKeyConfigured = !profile?.apiKey.isNullOrBlank(),
                    modelId = modelId,
                    modelContextLimit = model?.contextLimit,
                    modelOutputLimit = model?.outputLimit,
                    modelCapabilities = model?.capabilities.orEmpty().sorted(),
                    modelCapabilitiesComplete = model?.capabilitiesComplete ?: false,
                ),
            contextWindow = appConfig.contextLength,
            timeoutSeconds = appConfig.timeoutSeconds,
            maxParallelSubAgents = appConfig.maxParallelSubAgents,
            warnings = warnings,
            warningCodes = warningCodes,
        )
    }
}

internal fun diagnosticEndpointOrigin(value: String): String? =
    runCatching {
        val uri = URI(value)
        require(uri.isAbsolute && uri.host != null)
        URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString().trimEnd('/')
    }.getOrNull()
