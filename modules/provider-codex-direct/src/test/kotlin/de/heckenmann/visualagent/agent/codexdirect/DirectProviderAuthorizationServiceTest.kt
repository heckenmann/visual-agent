package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAuthorizationCatalog
import de.heckenmann.visualagent.agent.provider.ProviderAuthorizationSession
import de.heckenmann.visualagent.agent.provider.ProviderAuthorizationTarget
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.netty.handler.codec.http.QueryStringDecoder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectProviderAuthorizationServiceTest {
    @TempDir
    lateinit var directory: Path
    private val running = mutableListOf<DirectProviderAuthorizationService>()

    @AfterEach
    fun stopListeners() = running.forEach { StepVerifier.create(shutdown(it)).verifyComplete() }

    @Test
    fun `loopback callback stores validated credentials and returns only their reference`() {
        val protocol = mockk<DirectOAuthProtocol>()
        val record = record()
        every { protocol.exchange(any(), any(), any()) } returns Mono.just(record)
        val service = service(protocol)
        val result =
            service.begin(profile()).flatMap { session ->
                Mono
                    .zip(
                        service.await(session.id),
                        WebClient
                            .create()
                            .get()
                            .uri(callback(session))
                            .retrieve()
                            .bodyToMono(String::class.java),
                    ).map { it.t1 }
            }

        StepVerifier
            .create(
                result.flatMap { safe ->
                    assertEquals("ChatGPT d-client", safe.accountLabel)
                    store().load(safe.credentialReference, "profile")
                },
            ).expectNext(record)
            .verifyComplete()
    }

    @Test
    fun `catalog failure never reports a saved login and invalidates the new credentials`() {
        val protocol = mockk<DirectOAuthProtocol>()
        every { protocol.exchange(any(), any(), any()) } returns Mono.just(record())
        var reference = ""
        val catalog = authorizationCatalog()
        every { catalog.complete(any(), any()) } answers {
            reference = secondArg()
            Mono.error(IllegalStateException("Synthetic catalog failure"))
        }
        val service = service(protocol, catalog = catalog)
        StepVerifier
            .create(
                service.begin(profile()).flatMap { session ->
                    Mono.zip(
                        service.await(session.id),
                        WebClient
                            .create()
                            .get()
                            .uri(callback(session))
                            .retrieve()
                            .bodyToMono(String::class.java),
                    )
                },
            ).expectErrorMatches { it.cause?.message == "Synthetic catalog failure" }
            .verify()

        StepVerifier
            .create(store().load(reference, profile().id))
            .assertNext {
                assertEquals("", it.accessToken)
                assertEquals("", it.refreshToken)
            }.verifyComplete()
    }

    @Test
    fun `expiry clears credentials from a successful callback whose result was never claimed`() {
        val protocol = mockk<DirectOAuthProtocol>()
        every { protocol.exchange(any(), any(), any()) } returns Mono.just(record())
        val service = service(protocol)

        StepVerifier
            .create(
                service.begin(profile()).flatMap { session ->
                    WebClient
                        .create()
                        .get()
                        .uri(callback(session))
                        .retrieve()
                        .bodyToMono(String::class.java)
                        .then(
                            Mono
                                .fromCallable {
                                    Files.list(location().path).use { files ->
                                        files
                                            .filter { it.fileName.toString().endsWith(".json") }
                                            .findFirst()
                                            .orElseThrow()
                                            .fileName
                                            .toString()
                                            .removeSuffix(".json")
                                    }
                                }.subscribeOn(Schedulers.boundedElastic()),
                        ).flatMap { reference ->
                            store()
                                .load(reference, "profile")
                                .doOnNext { assertEquals("refresh", it.refreshToken) }
                                .then(service.expire(session.id))
                                .then(store().load(reference, "profile"))
                                .doOnNext { cleared ->
                                    assertEquals("", cleared.accessToken)
                                    assertEquals("", cleared.refreshToken)
                                    assertEquals("", cleared.idToken)
                                    assertEquals(emptySet(), cleared.scopes)
                                }.then(service.await(session.id))
                        }
                },
            ).expectError(IllegalArgumentException::class.java)
            .verify()
    }

    @Test
    fun `wrong callback state does not exchange credentials or consume the legitimate attempt`() {
        val protocol = mockk<DirectOAuthProtocol>()
        val service = service(protocol)

        StepVerifier
            .create(
                service.begin(profile()).flatMap { session ->
                    WebClient
                        .create()
                        .get()
                        .uri(callback(session, "wrong"))
                        .exchangeToMono { response ->
                            assertEquals(400, response.statusCode().value())
                            response.releaseBody()
                        }.then(service.cancel(session.id))
                },
            ).verifyComplete()

        verify(exactly = 0) { protocol.exchange(any(), any(), any()) }
    }

    @Test
    fun `cancelled attempt cannot subsequently be awaited`() {
        val service = service(mockk())

        StepVerifier
            .create(
                service.begin(profile()).flatMap { session ->
                    service.cancel(session.id).then(service.await(session.id))
                },
            ).expectError(IllegalArgumentException::class.java)
            .verify()
    }

    @Test
    fun `shutdown clears unclaimed credentials before completing Spring lifecycle callback`() {
        val protocol = mockk<DirectOAuthProtocol>()
        every { protocol.exchange(any(), any(), any()) } returns Mono.just(record())
        val service = service(protocol)

        StepVerifier
            .create(
                service.begin(profile()).flatMap { session ->
                    WebClient
                        .create()
                        .get()
                        .uri(callback(session))
                        .retrieve()
                        .bodyToMono(String::class.java)
                        .then(shutdown(service))
                        .then(
                            Mono
                                .fromCallable {
                                    Files.list(location().path).use { paths ->
                                        paths.filter { it.fileName.toString().endsWith(".json") }.toList()
                                    }
                                }.subscribeOn(Schedulers.boundedElastic()),
                        ).flatMap { paths ->
                            store().load(
                                paths
                                    .single()
                                    .fileName
                                    .toString()
                                    .removeSuffix(".json"),
                                "profile",
                            )
                        }
                },
            ).assertNext {
                assertEquals("", it.accessToken)
                assertEquals("", it.refreshToken)
                assertEquals("", it.idToken)
            }.verifyComplete()
        StepVerifier.create(service.begin(profile())).expectError(IllegalStateException::class.java).verify()
    }

    @Test
    fun `only one waiter can claim credentials and rejected claims cannot sign out the winner`() {
        val protocol = mockk<DirectOAuthProtocol>()
        every { protocol.exchange(any(), any(), any()) } returns Mono.just(record())
        val service = service(protocol)
        val result =
            service.begin(profile()).flatMap { session ->
                Mono
                    .zip(
                        service.await(session.id).materialize(),
                        service.await(session.id).materialize(),
                        WebClient
                            .create()
                            .get()
                            .uri(callback(session))
                            .retrieve()
                            .bodyToMono(String::class.java),
                    ).flatMap { outcomes ->
                        val claims = listOf(outcomes.t1, outcomes.t2)
                        assertEquals(1, claims.count { it.isOnNext })
                        assertEquals(1, claims.count { it.isOnError })
                        val winner = requireNotNull(claims.single { it.isOnNext }.get())
                        store().load(winner.credentialReference, "profile")
                    }
            }

        StepVerifier.create(result).assertNext { assertTrue(it.refreshToken.isNotEmpty()) }.verifyComplete()
    }

    @Test
    fun `shutdown continues cleaning other attempts after one credential failure`() {
        val credentials = mockk<DirectOAuthCredentialStore>()
        every { credentials.signOut(any(), any()) } returnsMany listOf(Mono.error(IllegalStateException("unavailable")), Mono.just(false))
        val service = service(mockk(), credentials)

        StepVerifier.create(service.begin(profile()).then(service.begin(profile())).then(shutdown(service))).verifyComplete()

        verify(exactly = 2) { credentials.signOut(any(), "profile") }
        StepVerifier.create(service.begin(profile())).expectError(IllegalStateException::class.java).verify()
    }

    @Test
    fun `API key mode does not open an OAuth callback listener`() {
        StepVerifier
            .create(service(mockk()).begin(profile().copy(directAuthMode = DirectProviderAuthMode.API_KEY)))
            .expectError(IllegalArgumentException::class.java)
            .verify()
    }

    @Test
    fun `installation host identity survives a new service instance`() {
        StepVerifier
            .create(
                DirectInstallationId(location()).load().flatMap { first ->
                    DirectInstallationId(location()).load().map { second -> assertEquals(first, second) }
                },
            ).expectNext(Unit)
            .verifyComplete()
    }

    private fun callback(
        session: ProviderAuthorizationSession,
        stateOverride: String? = null,
    ): String {
        val parameters = QueryStringDecoder(session.authorizationUrl).parameters()
        return parameters.getValue("redirect_uri").single() + "?state=" +
            (stateOverride ?: parameters.getValue("state").single()) + "&code=synthetic-code&client_id=issued-client"
    }

    private fun shutdown(service: DirectProviderAuthorizationService): Mono<Void> = Mono.create { sink -> service.stop { sink.success() } }

    private fun service(
        protocol: DirectOAuthProtocol,
        credentials: DirectOAuthCredentialStore = store(),
        catalog: ProviderAuthorizationCatalog = authorizationCatalog(),
    ) = DirectProviderAuthorizationService(
        DirectInstallationId(location()),
        credentials,
        protocol,
        DirectOAuthSessionLock(location()),
        catalog,
    ).also(running::add)

    private fun authorizationCatalog() =
        mockk<ProviderAuthorizationCatalog> {
            every { prepare(any()) } answers { Mono.just(ProviderAuthorizationTarget(firstArg(), null)) }
            every { complete(any(), any()) } returns Mono.empty()
            every { clear(any()) } returns Mono.empty()
        }

    private fun location() = DirectCredentialDirectory(directory.resolve("oauth"))

    private fun store() = DirectOAuthCredentialStore(location())

    private fun profile() =
        ProviderProfile(
            "profile",
            "Direct",
            ProviderAdapter.CODEX_DIRECT,
            "https://api.openai.com",
            directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
        )

    private fun record() =
        DirectOAuthCredentials(
            "profile",
            "issued-client",
            "subject",
            DirectOAuthAttempt.ISSUER,
            "access",
            "refresh",
            "identity",
            1000,
            setOf("chatgpt.tokens.use.direct"),
        )
}
