package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingException
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CodexDirectProviderTest {
    @Test
    fun `vision retains an explicitly requested model instead of the profile default`() {
        val catalog = mockk<ProviderCatalogService>()
        val profile = profile().copy(defaultModel = "default-model")
        every { catalog.activeProviderId() } returns profile.id
        every { catalog.getProvider(profile.id) } returns profile
        val provider = spyk(provider(mockk(), WebClient.create(), catalog))
        val image = byteArrayOf(1, 2, 3)
        val response = ChatResponse("selected-model", Message("assistant", "description"), true)
        every { provider.visionReactive(image, "describe", "selected-model", profile) } returns Mono.just(response)

        StepVerifier
            .create(provider.visionReactive(image, "describe", "selected-model"))
            .expectNext(response)
            .verifyComplete()

        verify(exactly = 1) { provider.visionReactive(image, "describe", "selected-model", profile) }
    }

    @Test
    fun `discovery uses the selected bearer and does not invent capabilities or entitlement`() {
        val credentials = mockk<DirectProviderCredentials>()
        val profile = profile()
        every { credentials.bearer(profile) } returns Mono.just("synthetic-token")
        val client =
            WebClient
                .builder()
                .exchangeFunction { request ->
                    assertEquals("https://api.openai.com/v1/models", request.url().toString())
                    assertEquals("Bearer synthetic-token", request.headers().getFirst("Authorization"))
                    Mono.just(
                        ClientResponse
                            .create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("""{"data":[{"id":"model-a"},{"id":"model-b"}]}""")
                            .build(),
                    )
                }.build()

        StepVerifier
            .create(provider(credentials, client).loadModelsReactive(profile))
            .assertNext { models ->
                assertEquals(listOf("model-a", "model-b"), models.map { it.id })
                models.forEach {
                    assertFalse(it.capabilitiesComplete)
                    assertEquals(emptySet(), it.capabilities)
                }
            }.verifyComplete()
    }

    @Test
    fun `untrusted endpoints are rejected before obtaining credentials`() {
        val credentials = mockk<DirectProviderCredentials>()

        StepVerifier
            .create(provider(credentials, WebClient.create()).loadModelsReactive(profile().copy(baseUrl = "https://other.example")))
            .expectErrorMatches { it is ProviderUserFacingException && it.userFacing.summary == "Provider configuration invalid" }
            .verify()

        verify(exactly = 0) { credentials.bearer(any()) }
    }

    @Test
    fun `failed discovery does not replace persisted models with an empty list`() {
        val credentials = mockk<DirectProviderCredentials>()
        every { credentials.bearer(any()) } returns Mono.just("synthetic-token")
        val catalog = mockk<ProviderCatalogService>()
        val client =
            WebClient
                .builder()
                .exchangeFunction {
                    Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())
                }.build()

        StepVerifier
            .create(provider(credentials, client, catalog).loadModelsReactive(profile()))
            .expectError()
            .verify()

        verify(exactly = 0) { catalog.updateDiscoveredModelConfigs(any(), any()) }
    }

    private fun provider(
        credentials: DirectProviderCredentials,
        client: WebClient,
        catalog: ProviderCatalogService = mockk(),
    ): CodexDirectProvider {
        val tools = mockk<ProviderToolCallbacks>()
        return CodexDirectProvider(catalog, credentials, mockk(), OpenAiPromptFactory(tools), ReactiveResponsesToolLoop(tools), client)
    }

    private fun profile() = ProviderProfile("direct", "Direct", ProviderAdapter.CODEX_DIRECT, "https://api.openai.com")
}
