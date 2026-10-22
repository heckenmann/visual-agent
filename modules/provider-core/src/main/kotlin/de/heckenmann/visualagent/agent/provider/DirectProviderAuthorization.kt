package de.heckenmann.visualagent.agent.provider

import reactor.core.publisher.Mono

/** Browser authorization instructions; URLs may contain identity hints and must not be logged. */
data class ProviderAuthorizationSession(
    val id: String,
    val authorizationUrl: String,
) {
    override fun toString(): String = "ProviderAuthorizationSession(redacted)"
}

/** Safe result of authorizing a server-owned registration, containing no tokens. */
data class ProviderAuthorizationResult(
    val credentialReference: String,
    val accountLabel: String,
)

/** Server-owned authentication lifecycle for the direct Codex provider. */
interface DirectProviderAuthorization {
    /** Starts a one-use browser authorization attempt for an explicitly selected OAuth profile. */
    fun begin(profile: ProviderProfile): Mono<ProviderAuthorizationSession>

    /** Awaits a validated result; cancelling the subscription cancels the corresponding attempt. */
    fun await(sessionId: String): Mono<ProviderAuthorizationResult>

    /** Cancels one outstanding attempt and disposes its callback listener. */
    fun cancel(sessionId: String): Mono<Void>

    /** Revokes and clears the selected session, returning whether remote revocation was confirmed. */
    fun signOut(profile: ProviderProfile): Mono<Boolean>
}
