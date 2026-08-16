package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.config.AppConfigBean
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Verifies that welcome generation validates the provider-catalog selection. */
class WelcomeMessageComposerTest {
    @Test
    fun `welcome ignores stale legacy ollama model when codex model is active`() =
        runTest {
            val provider = mockk<LLMProvider>()
            val catalog = mockk<ProviderCatalogService>()
            val config = AppConfigBean().apply { ollamaModel = "minimax-m2.7:cloud" }
            val requestSlot = slot<ChatRequestContext>()
            every { catalog.activeModelId() } returns "gpt-5.6-luna"
            coEvery { provider.checkConnection() } returns true
            coEvery { provider.getModels() } returns listOf("gpt-5.6-luna")
            coEvery { provider.chat(capture(requestSlot)) } returns
                ChatResponse("gpt-5.6-luna", Message("assistant", "Hallo"), true)
            val persisted = mutableListOf<Message>()

            val result =
                WelcomeMessageComposer(provider, config, catalog).compose { message ->
                    persisted += message
                    message
                }

            assertIs<WelcomeResult.Generated>(result)
            assertEquals(listOf("Hallo"), persisted.map(Message::content))
            val userMessage = requestSlot.captured.messages.last()
            assertEquals(
                "user",
                userMessage.role,
            )
            assertEquals(
                "Generate the welcome message now.",
                userMessage.content,
            )
        }
}
