package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

class DirectProviderCredentialsTest {
    @TempDir
    lateinit var directory: Path
    private val clock = Clock.fixed(Instant.ofEpochSecond(2000), ZoneOffset.UTC)

    @Test
    fun `API key mode ignores OAuth references and never invokes refresh`() {
        val protocol = mockk<DirectOAuthProtocol>()
        val profile = profile().copy(directAuthMode = DirectProviderAuthMode.API_KEY, apiKey = "synthetic-key")

        StepVerifier.create(service(protocol).bearer(profile)).expectNext("synthetic-key").verifyComplete()

        verify(exactly = 0) { protocol.refresh(any()) }
    }

    @Test
    fun `OAuth mode never falls back to a stored API key`() {
        val profile = profile().copy(apiKey = "must-not-be-used")

        StepVerifier
            .create(service(mockk()).bearer(profile))
            .expectErrorMatches { it is IllegalStateException && it.message == "Sign in with ChatGPT again" }
            .verify()
    }

    @Test
    fun `concurrent callers reuse the newly rotated token after session serialization`() {
        val profile = profile()
        val protocol = mockk<DirectOAuthProtocol>()
        val completed = Sinks.one<DirectOAuthCredentials>()
        val record = record()
        every { protocol.refresh(any()) } returns completed.asMono()
        StepVerifier.create(store().save(requireNotNull(profile.credentialReference), record, null)).expectNext(true).verifyComplete()
        val service = service(protocol)

        StepVerifier
            .create(Flux.merge(service.bearer(profile), service.bearer(profile)))
            .then { completed.tryEmitValue(record.copy(accessToken = "rotated", expiresAtEpochSeconds = 4000, generation = 1)) }
            .expectNext("rotated", "rotated")
            .verifyComplete()

        verify(exactly = 1) { protocol.refresh(record) }
        StepVerifier
            .create(store().load(requireNotNull(profile.credentialReference), profile.id))
            .assertNext { assertEquals(1L, it.generation) }
            .verifyComplete()
    }

    private fun location() = DirectCredentialDirectory(directory.resolve("oauth"))

    private fun store() = DirectOAuthCredentialStore(location())

    private fun service(protocol: DirectOAuthProtocol) =
        DirectProviderCredentials(store(), protocol, DirectOAuthSessionLock(location()), clock)

    private fun profile() =
        ProviderProfile(
            "profile",
            "Direct",
            ProviderAdapter.OPENAI_COMPATIBLE,
            "https://api.openai.com",
            directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
            credentialReference = UUID.randomUUID().toString(),
        )

    private fun record() =
        DirectOAuthCredentials(
            "profile",
            "issued",
            "subject",
            DirectOAuthAttempt.ISSUER,
            "old",
            "refresh",
            "identity",
            1000,
            setOf("chatgpt.tokens.use.direct"),
        )
}
