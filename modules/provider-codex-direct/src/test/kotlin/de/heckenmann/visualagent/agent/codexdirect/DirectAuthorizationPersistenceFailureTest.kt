package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.DefaultProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAuthorizationCatalog
import de.heckenmann.visualagent.agent.provider.ProviderCatalogNotifications
import de.heckenmann.visualagent.agent.provider.ProviderCatalogPersistence
import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals

/** Exercises real credential and catalog persistence when post-commit notification reads fail. */
class DirectAuthorizationPersistenceFailureTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `post-commit notification failure cannot tombstone the successfully registered credentials`() {
        val preferences = FailingNotificationPreferences()
        val persistence = ProviderCatalogPersistence(preferences)
        val notifications = ProviderCatalogNotifications(persistence, DefaultProviderRuntimeConfig())
        val catalog = ProviderAuthorizationCatalog(persistence, notifications)
        val location = DirectCredentialDirectory(directory.resolve("credentials"))
        val store = DirectOAuthCredentialStore(location)
        val protocol = mockk<DirectOAuthProtocol>()
        val record =
            DirectOAuthCredentials(
                "profile",
                "synthetic-client",
                "synthetic-subject",
                DirectOAuthAttempt.ISSUER,
                "synthetic-access",
                "synthetic-refresh",
                "synthetic-identity",
                1000,
                setOf("chatgpt.tokens.use.direct"),
            )
        every { protocol.exchange(any(), any(), any()) } returns Mono.just(record)
        val service =
            DirectProviderAuthorizationService(
                DirectInstallationId(location),
                store,
                protocol,
                DirectOAuthSessionLock(location),
                catalog,
            )
        val profile =
            ProviderProfile(
                "profile",
                "Direct",
                ProviderAdapter.CODEX_DIRECT,
                "https://api.openai.com",
                directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
            )
        try {
            StepVerifier
                .create(
                    service
                        .begin(profile)
                        .flatMap { session ->
                            val query = QueryStringDecoder(session.authorizationUrl).parameters()
                            val callback =
                                query.getValue("redirect_uri").single() + "?state=" +
                                    query.getValue("state").single() + "&code=synthetic&client_id=synthetic-client"
                            Mono
                                .zip(
                                    service.await(session.id),
                                    WebClient
                                        .create()
                                        .get()
                                        .uri(callback)
                                        .retrieve()
                                        .bodyToMono(String::class.java),
                                ).map { it.t1 }
                        }.flatMap { result ->
                            val saved =
                                Json
                                    .parseToJsonElement(preferences.encoded)
                                    .jsonObject
                                    .getValue("providers")
                                    .jsonArray
                                    .single()
                                    .jsonObject
                            assertEquals(result.credentialReference, saved.getValue("credentialReference").jsonPrimitive.content)
                            store.load(result.credentialReference, profile.id)
                        },
                ).expectNext(record)
                .verifyComplete()
            assertEquals(true, preferences.notificationFailed.get())
        } finally {
            StepVerifier.create(Mono.create<Void> { sink -> service.stop { sink.success() } }).verifyComplete()
        }
    }

    private class FailingNotificationPreferences : ProviderPreferenceStore {
        var encoded = ""
        val notificationFailed = AtomicBoolean()
        private val failRead = AtomicBoolean()

        override fun getPreference(key: String): String? {
            if (failRead.compareAndSet(true, false)) {
                notificationFailed.set(true)
                error("Synthetic notification failure")
            }
            return encoded.takeIf(String::isNotEmpty)
        }

        override fun setPreference(
            key: String,
            value: String,
        ) {
            encoded = value
        }

        override fun compareAndSetPreferenceReactive(
            key: String,
            expected: String?,
            value: String,
        ): Mono<Boolean> =
            Mono.fromCallable {
                if (encoded.takeIf(String::isNotEmpty) != expected) {
                    false
                } else {
                    encoded = value
                    failRead.set(true)
                    true
                }
            }
    }
}
