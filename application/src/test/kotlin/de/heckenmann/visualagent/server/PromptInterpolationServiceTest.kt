package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ResolvedModelConfig
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.ConversationRecord
import de.heckenmann.visualagent.knowledge.ConversationStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises reactive routing, bounded context and disposal of provider work. */
class PromptInterpolationServiceTest {
    private val provider = mockk<LLMProvider>()
    private val catalog = mockk<ProviderCatalogService>()
    private val store = mockk<ConversationStore>(relaxed = true)
    private val config = AppConfigBean()
    private val model = ProviderModelConfig("rewrite", contextLimit = 4096, outputLimit = 256)
    private val profile =
        ProviderProfile(
            "remote",
            "Remote",
            ProviderAdapter.OPENAI_COMPATIBLE,
            "https://example.org",
            apiKey = "not-in-prompt",
            models = listOf(model),
        )
    private val service = PromptInterpolationService(provider, catalog, store, config)

    private fun configure() {
        every { catalog.resolve(any(), any(), any(), any()) } returns ResolvedModelConfig(profile, model, null, emptyMap())
        every { store.getConversationMessagesReactive("main", any()) } returns
            Flux.just(
                row("user", "Discuss the report."),
                row("tool", "private tool result"),
                row("assistant", "The report is ready.<think>hidden</think>"),
            )
        every { provider.chatReactive(any<ChatRequestContext>()) } returns
            Mono.just(ChatResponse("rewrite", Message("assistant", "Improve the report."), true))
    }

    @Test
    fun `active snapshot includes safe context and output limit without tools or persistence`() {
        configure()
        val request = slot<ChatRequestContext>()
        every { provider.chatReactive(capture(request)) } returns
            Mono.just(ChatResponse("rewrite", Message("assistant", "<think>hidden</think>Improve the report."), true))
        assertEquals("Improve the report.", service.interpolate("improve it", CancellationToken()).block())
        verify {
            catalog.resolve(null, null, null, emptyMap())
            store.getConversationMessagesReactive("main", 8)
        }
        assertEquals(listOf("system", "user", "assistant", "user"), request.captured.messages.map { it.role })
        assertTrue(request.captured.enabledTools.isEmpty())
        assertEquals(256, request.captured.parameters.maxTokens)
        assertEquals(profile, request.captured.providerProfile)
        assertFalse(
            request.captured.messages.any {
                "hidden" in it.content ||
                    "not-in-prompt" in it.content ||
                    "private tool result" in it.content
            },
        )
        verify(exactly = 0) { store.saveConversationMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `dedicated assignment and configured window are honored`() {
        configure()
        config.promptInterpolationProviderId = "remote"
        config.promptInterpolationModelId = "rewrite"
        config.promptInterpolationContextMessages = 5
        assertEquals("Improve the report.", service.interpolate("improve it", CancellationToken()).block())
        verify {
            catalog.resolve("remote", "rewrite", null, emptyMap())
            store.getConversationMessagesReactive("main", 5)
        }
    }

    @Test
    fun `dedicated non chat model is rejected before invoking chat provider`() {
        configure()
        config.promptInterpolationProviderId = "remote"
        config.promptInterpolationModelId = "embedding-only"
        val embedding = ProviderModelConfig("embedding-only", capabilities = setOf("embedding"), capabilitiesComplete = true)
        every { catalog.resolve(any(), any(), any(), any()) } returns
            ResolvedModelConfig(profile.copy(models = listOf(embedding)), embedding, null, emptyMap())

        StepVerifier
            .create(service.interpolate("Improve the request", CancellationToken()))
            .expectErrorMatches { it is IllegalArgumentException && it.message.orEmpty().contains("CHAT") }
            .verify()
        verify(exactly = 0) { provider.chatReactive(any<ChatRequestContext>()) }
    }

    @Test
    fun `active non chat model is also rejected before provider access`() {
        configure()
        val embedding = ProviderModelConfig("embedding-only", capabilities = setOf("embedding"), capabilitiesComplete = true)
        every { catalog.resolve(any(), any(), any(), any()) } returns
            ResolvedModelConfig(profile.copy(models = listOf(embedding)), embedding, null, emptyMap())

        StepVerifier
            .create(service.interpolate("Improve the request", CancellationToken()))
            .expectErrorMatches { it is IllegalArgumentException && it.message.orEmpty().contains("CHAT") }
            .verify()
        verify(exactly = 0) { provider.chatReactive(any<ChatRequestContext>()) }
    }

    @Test
    fun `invalid assignment fails without provider access`() {
        configure()
        config.promptInterpolationProviderId = "remote"
        config.promptInterpolationModelId = "missing"
        StepVerifier.create(service.interpolate("draft", CancellationToken())).expectError(IllegalArgumentException::class.java).verify()
        verify(exactly = 0) { provider.chatReactive(any<ChatRequestContext>()) }
    }

    @Test
    fun `invalid and already cancelled drafts make no provider request`() {
        configure()
        for (draft in listOf(" ", "a".repeat(16385))) {
            StepVerifier.create(service.interpolate(draft, CancellationToken())).expectError(IllegalArgumentException::class.java).verify()
        }
        StepVerifier
            .create(
                service.interpolate(
                    "draft",
                    CancellationToken().also {
                        it.cancel()
                    },
                ),
            ).expectError(CancellationException::class.java)
            .verify()
        verify(exactly = 0) { provider.chatReactive(any<ChatRequestContext>()) }
    }

    @Test
    fun `empty absent and failed responses propagate without a replacement draft`() {
        configure()
        for (response in listOf(
            Mono.just(ChatResponse("rewrite", Message("assistant", "<think>hidden</think>"), true)),
            Mono.empty(),
            Mono.error(IllegalStateException("offline")),
        )) {
            every { provider.chatReactive(any<ChatRequestContext>()) } returns response
            StepVerifier.create(service.interpolate("draft", CancellationToken())).expectError().verify()
        }
    }

    @Test
    fun `cancellation disposes a provider that ignores the token`() {
        configure()
        val token = CancellationToken()
        val disposed = CountDownLatch(1)
        every { provider.chatReactive(any<ChatRequestContext>()) } returns
            Mono.never<ChatResponse>().doOnRequest { token.cancel() }.doOnCancel { disposed.countDown() }
        StepVerifier
            .create(
                service.interpolate("draft", token),
            ).expectError(CancellationException::class.java)
            .verify(Duration.ofSeconds(5))
        assertTrue(disposed.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `timeout cancels stalled preparation without returning a draft`() {
        configure()
        val token = CancellationToken()
        every { provider.chatReactive(any<ChatRequestContext>()) } returns Mono.never()
        StepVerifier
            .withVirtualTime { service.interpolate("draft", token) }
            .thenAwait(Duration.ofSeconds(31))
            .expectError(java.util.concurrent.TimeoutException::class.java)
            .verify(Duration.ofSeconds(5))
        assertTrue(token.isCancelled)
    }

    private fun row(
        role: String,
        text: String,
    ) = ConversationRecord("$role-id", role, text, null, Instant.EPOCH)
}
