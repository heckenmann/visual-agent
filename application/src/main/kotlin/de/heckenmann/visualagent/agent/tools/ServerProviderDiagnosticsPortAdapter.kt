package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnosticsPort
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** Probes the configured provider's read-only model-discovery API without saving its response. */
@Component
class ServerProviderDiagnosticsPortAdapter(
    private val catalog: ProviderCatalogService,
    @Lazy
    private val provider: LLMProvider,
) : ServerProviderDiagnosticsPort {
    override fun check(): Mono<ServerProviderDiagnostic> =
        Mono
            .fromCallable {
                val profile = catalog.getProvider(catalog.activeProviderId())
                val modelId = catalog.activeModelId()
                profile to modelId
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { (profile, modelId) ->
                if (profile == null || !profile.enabled) {
                    Mono.just(
                        ServerProviderDiagnostic(
                            providerId = profile?.id.orEmpty(),
                            adapter = profile?.adapter?.name.orEmpty(),
                            enabled = false,
                            credentialConfigured = !profile?.apiKey.isNullOrBlank(),
                            discoveredModelCount = null,
                            selectedModelAvailable = null,
                            failureKind = "provider_not_enabled",
                            endpointOrigin = profile?.baseUrl?.let(::diagnosticEndpointOrigin),
                            selectedModelId = modelId,
                        ),
                    )
                } else {
                    provider
                        .getModelConfigsReactive(profile)
                        .map { models ->
                            val selected = models.firstOrNull { it.id == modelId }
                            ServerProviderDiagnostic(
                                providerId = profile.id,
                                adapter = profile.adapter.name,
                                enabled = true,
                                credentialConfigured = !profile.apiKey.isNullOrBlank(),
                                discoveredModelCount = models.size,
                                selectedModelAvailable = selected != null,
                                failureKind = null,
                                endpointOrigin = diagnosticEndpointOrigin(profile.baseUrl),
                                selectedModelId = modelId,
                                selectedModelCapabilities = selected?.capabilities?.sorted(),
                                selectedModelCapabilitiesComplete = selected?.capabilitiesComplete,
                            )
                        }.onErrorResume { error ->
                            Mono.just(
                                ServerProviderDiagnostic(
                                    providerId = profile.id,
                                    adapter = profile.adapter.name,
                                    enabled = true,
                                    credentialConfigured = !profile.apiKey.isNullOrBlank(),
                                    discoveredModelCount = null,
                                    selectedModelAvailable = null,
                                    failureKind = ProviderErrorMessages.userFacingError(error).summary,
                                    endpointOrigin = diagnosticEndpointOrigin(profile.baseUrl),
                                    selectedModelId = modelId,
                                ),
                            )
                        }
                }
            }
}
