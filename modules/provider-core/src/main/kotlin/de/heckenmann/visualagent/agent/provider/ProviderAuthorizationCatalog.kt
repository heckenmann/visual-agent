package de.heckenmann.visualagent.agent.provider

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/** Captures the authorized profile and its persisted authentication route before opening a browser. */
data class ProviderAuthorizationTarget(
    val profile: ProviderProfile,
    val previous: ProviderProfile?,
) {
    override fun toString(): String = "ProviderAuthorizationTarget(redacted)"
}

/** Persists explicit authorization independently of staged model and conversation settings. */
@Service
class ProviderAuthorizationCatalog(
    private val persistence: ProviderCatalogPersistence,
    private val notifications: ProviderCatalogNotifications,
) {
    /** Captures a database-backed precondition without saving unrelated settings edits. */
    fun prepare(profile: ProviderProfile): Mono<ProviderAuthorizationTarget> =
        persistence.loadReactive().map { state ->
            ProviderAuthorizationTarget(profile, state.providers.firstOrNull { it.id == profile.id })
        }

    /** Attaches a registration only if the provider's authentication route has not changed. */
    fun complete(
        target: ProviderAuthorizationTarget,
        reference: String,
    ): Mono<Void> =
        persistence
            .mutateReactive { state ->
                val current = state.providers.firstOrNull { it.id == target.profile.id }
                check(current.sameAuthorization(target.previous)) { "Provider authentication changed during sign-in; retry sign-in" }
                val registered =
                    (current ?: target.profile).copy(
                        adapter = ProviderAdapter.CODEX_DIRECT,
                        directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
                        credentialReference = reference,
                        apiKey = "",
                    )
                state.copy(providers = state.providers.filterNot { it.id == registered.id } + registered)
            }.flatMap { notifications.publishReactive() }

    /** Removes only the explicitly signed-out registration, preserving newer account selections. */
    fun clear(profile: ProviderProfile): Mono<Void> =
        persistence
            .mutateReactive { state ->
                state.copy(
                    providers =
                        state.providers.map { current ->
                            if (current.id == profile.id && current.credentialReference == profile.credentialReference) {
                                current.copy(credentialReference = null)
                            } else {
                                current
                            }
                        },
                )
            }.flatMap { notifications.publishReactive() }
}

private fun ProviderProfile?.sameAuthorization(other: ProviderProfile?): Boolean =
    if (this == null || other == null) {
        this == other
    } else {
        adapter == other.adapter &&
            directAuthMode == other.directAuthMode &&
            credentialReference == other.credentialReference &&
            apiKey == other.apiKey
    }

/** Prevents stale settings drafts from replacing or resurrecting server-owned registrations. */
internal fun CatalogState.withAuthoritativeCredentials(current: CatalogState): CatalogState =
    copy(
        providers =
            providers.map { proposed ->
                proposed.withAuthoritativeCredential(current.providers.firstOrNull { it.id == proposed.id })
            },
    )

/** Applies the persisted registration to settings edits; only authorization may change the reference. */
internal fun ProviderProfile.withAuthoritativeCredential(current: ProviderProfile?): ProviderProfile =
    if (adapter == ProviderAdapter.CODEX_DIRECT && directAuthMode == DirectProviderAuthMode.CHATGPT_OAUTH) {
        copy(credentialReference = current?.credentialReference, apiKey = "")
    } else {
        this
    }
