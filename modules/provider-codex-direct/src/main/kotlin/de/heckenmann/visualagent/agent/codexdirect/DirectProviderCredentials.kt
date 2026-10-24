package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Clock

/** Resolves only the explicitly selected credential route and never substitutes a billing mode. */
@Service
internal class DirectProviderCredentials(
    private val store: DirectOAuthCredentialStore,
    private val protocol: DirectOAuthProtocol,
    private val sessions: DirectOAuthSessionLock,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** Resolves fresh bearer credentials for one provider profile without exposing them in metadata. */
    fun bearer(profile: ProviderProfile): Mono<String> =
        Mono.defer {
            when (profile.directAuthMode) {
                DirectProviderAuthMode.API_KEY -> {
                    require(profile.apiKey.isNotBlank()) { "Configure an OpenAI Platform API key" }
                    Mono.just(profile.apiKey)
                }
                DirectProviderAuthMode.CHATGPT_OAUTH -> {
                    val reference = requireNotNull(profile.credentialReference) { "Sign in with ChatGPT first" }
                    sessions.withSession(reference) {
                        store
                            .load(reference, profile.id)
                            .switchIfEmpty(Mono.error(IllegalStateException("Sign in with ChatGPT again")))
                            .flatMap { record ->
                                require(record.accessToken.isNotBlank() && "chatgpt.tokens.use.direct" in record.scopes) {
                                    "Sign in with ChatGPT again"
                                }
                                if (record.expiresAtEpochSeconds > clock.instant().epochSecond + 60) {
                                    Mono.just(record.accessToken)
                                } else {
                                    protocol.refresh(record).flatMap { refreshed ->
                                        store.save(reference, refreshed, record.generation).map { saved ->
                                            check(saved) { "The ChatGPT session changed; retry with the current account" }
                                            refreshed.accessToken
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
}
