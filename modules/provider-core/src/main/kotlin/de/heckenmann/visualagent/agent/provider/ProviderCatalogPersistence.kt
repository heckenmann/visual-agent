package de.heckenmann.visualagent.agent.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import reactor.core.publisher.Mono

/** Persisted catalog snapshot used by database-conditional mutations. */
@Serializable
internal data class CatalogState(
    val version: Int = 1,
    val activeProviderId: String = "ollama",
    val activeModelId: String = "",
    val providers: List<ProviderProfile> = emptyList(),
)

/** Recomputes mutations against fresh database state after a compare-and-set conflict. */
@org.springframework.stereotype.Service
class ProviderCatalogPersistence(
    private val preferences: ProviderPreferenceStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    internal fun load(): CatalogState = decode(preferences.getPreference(KEY_CATALOG))

    internal fun mutate(transform: (CatalogState) -> CatalogState): CatalogState = requireNotNull(mutateReactive(transform).block())

    internal fun mutateReactive(transform: (CatalogState) -> CatalogState): Mono<CatalogState> = attempt(transform, 16)

    private fun attempt(
        transform: (CatalogState) -> CatalogState,
        remaining: Int,
    ): Mono<CatalogState> =
        Mono.defer {
            preferences
                .getPreferenceReactive(KEY_CATALOG)
                .map { java.util.Optional.of(it) }
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap { value ->
                    val encoded = value.orElse(null)
                    val current = decode(encoded)
                    val next = transform(current)
                    if (encoded != null && next == current) return@flatMap Mono.just(current)
                    preferences
                        .compareAndSetPreferenceReactive(KEY_CATALOG, encoded, json.encodeToString(next))
                        .flatMap { updated ->
                            when {
                                updated -> Mono.just(next)
                                remaining > 1 -> attempt(transform, remaining - 1)
                                else -> Mono.error(IllegalStateException("Provider catalog changed concurrently; retry the operation"))
                            }
                        }
                }
        }

    private fun decode(encoded: String?): CatalogState =
        encoded?.let { runCatching { json.decodeFromString<CatalogState>(it) }.getOrNull() } ?: CatalogState()

    private companion object {
        const val KEY_CATALOG = "llm.provider.catalog.v1"
    }
}
