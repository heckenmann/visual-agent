package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.messages.part.OpaquePayload
import org.springframework.ai.chat.messages.part.ReasoningPart
import org.springframework.ai.chat.messages.part.TextPart
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.MediaContent
import org.springframework.ai.tokenizer.TokenCountEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderReplayBudgetTest {
    @Test
    fun `native reasoning consumes context budget without displacing the latest user message`() {
        val replay =
            ProviderReplayCodec().encode(
                "profile",
                "model",
                AssistantMessage
                    .builder()
                    .parts(
                        listOf(
                            ReasoningPart(null, null, OpaquePayload("openai.responses", "encrypted_content", "x".repeat(1000)), emptyMap()),
                            TextPart.of("previous answer"),
                        ),
                    ).build(),
            )
        val plain = Message("assistant", "previous answer")
        val user = Message("user", "latest user request")
        val request =
            ChatRequestContext(
                listOf(plain, user),
                parameters = ModelParameters(maxTokens = 20),
                contextWindow = ContextWindow(configuredLimit = 300),
            )
        val budgeter = RequestContextBudgeter(LengthEstimator)

        assertTrue(budgeter.fit(request, request.messages).messages.any { it.role == "assistant" })
        val fitted = budgeter.fit(request, listOf(plain.copy(providerReplay = replay), user))

        assertEquals(user, fitted.messages.last())
        assertFalse(fitted.messages.any { it.role == "assistant" })
    }

    @Test
    fun `replay from another profile model or adapter cannot displace visible history`() {
        val replay =
            ProviderReplayCodec().encode(
                "original",
                "original-model",
                AssistantMessage
                    .builder()
                    .parts(
                        listOf(
                            ReasoningPart(null, null, OpaquePayload("openai.responses", "encrypted_content", "x".repeat(1000)), emptyMap()),
                            TextPart.of("previous answer"),
                        ),
                    ).build(),
            )
        val messages = listOf(Message("assistant", "previous answer", providerReplay = replay), Message("user", "latest request"))
        val profiles =
            listOf(
                ProviderProfile("other", "Other", ProviderAdapter.CODEX_DIRECT, "https://api.openai.com/v1"),
                ProviderProfile("original", "Other model", ProviderAdapter.CODEX_DIRECT, "https://api.openai.com/v1"),
                ProviderProfile("original", "Other adapter", ProviderAdapter.OPENAI_COMPATIBLE, "https://api.openai.com/v1"),
            )
        profiles.forEachIndexed { index, profile ->
            var status: ContextBudgetStatus? = null
            val request =
                ChatRequestContext(
                    messages,
                    model = if (index == 1) "other-model" else "original-model",
                    providerProfile = profile,
                    parameters = ModelParameters(maxTokens = 20),
                    contextWindow = ContextWindow(configuredLimit = 300),
                    onContextBudgeted = { status = it },
                )
            val retained = RequestContextBudgeter(LengthEstimator).fit(request, messages).messages
            assertEquals(messages.map { it.content }, retained.map { it.content })
            assertTrue(retained.all { it.providerReplay == null })
            assertFalse(requireNotNull(status).historyReduced)
        }
    }

    @Test
    fun `assembled native prompt parts are budgeted even for an adapter without persisted replay`() {
        val native =
            AssistantMessage
                .builder()
                .parts(
                    listOf(
                        ReasoningPart(null, null, OpaquePayload("openai.responses", "encrypted_content", "x".repeat(1000)), emptyMap()),
                        TextPart.of("previous answer"),
                    ),
                ).build()
        val user = UserMessage("latest request")
        val request =
            ChatRequestContext(
                emptyList(),
                model = "model",
                providerProfile = ProviderProfile("profile", "Profile", ProviderAdapter.OPENAI_COMPATIBLE, "https://api.openai.com/v1"),
                parameters = ModelParameters(maxTokens = 20),
                contextWindow = ContextWindow(configuredLimit = 300),
            )

        val fitted = RequestContextBudgeter(LengthEstimator).fitPrompt(request, Prompt(listOf(native, user)))

        assertEquals(listOf(user), fitted.instructions)
    }

    @Test
    fun `text already counted in a message is not counted again from the replay envelope`() {
        val replay = ProviderReplayCodec().encode("profile", "model", AssistantMessage("text"))

        assertEquals(0, ProviderReplayCodec().additionalTokens(replay, LengthEstimator))
    }

    private object LengthEstimator : TokenCountEstimator {
        override fun estimate(text: String?): Int = text?.length ?: 0

        override fun estimate(mediaContent: MediaContent): Int = 0

        override fun estimate(mediaContents: Iterable<MediaContent>): Int = 0
    }
}
