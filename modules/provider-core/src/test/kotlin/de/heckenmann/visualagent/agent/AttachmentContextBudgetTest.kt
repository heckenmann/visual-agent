package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.MediaContent
import org.springframework.ai.tokenizer.TokenCountEstimator
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentContextBudgetTest {
    private val budgeter = RequestContextBudgeter(LengthEstimator)

    @Test
    fun `attachment input displaces optional history rather than the latest user message`() {
        val messages = listOf(Message("assistant", "previous answer"), Message("user", "latest request"))
        val request =
            ChatRequestContext(messages, contextWindow = ContextWindow(configuredLimit = 100), parameters = ModelParameters(maxTokens = 10))
        assertEquals(messages, budgeter.fit(request, messages).messages)
        val budgeted = budgeter.fit(request.copy(additionalInputTokens = 60), messages)
        assertEquals(listOf(messages.last()), budgeted.messages)
        assertEquals(10, budgeted.parameters.maxTokens)
        assertEquals(60, budgeted.additionalInputTokens)
    }

    @Test
    fun `mandatory user text and output must still fit after native input is reserved`() {
        val messages = listOf(Message("user", "latest request"))
        listOf(100, 90).forEach { tokens ->
            assertFailsWith<ContextWindowExceededException> {
                budgeter.fit(
                    ChatRequestContext(messages, contextWindow = ContextWindow(configuredLimit = 100), additionalInputTokens = tokens),
                    messages,
                )
            }
        }
    }

    @Test
    fun `each continuation reserves the same attachment cost without double counting or losing it`() {
        val request = ChatRequestContext(emptyList(), contextWindow = ContextWindow(configuredLimit = 100), additionalInputTokens = 60)
        val prompt = Prompt(listOf(UserMessage("latest request")))
        repeat(3) {
            val fitted = budgeter.fitPrompt(request, prompt)
            assertEquals(prompt.instructions, fitted.instructions)
        }
        assertFailsWith<ContextWindowExceededException> {
            budgeter.fitPrompt(request.copy(additionalInputTokens = 90), prompt)
        }
    }

    @Test
    fun `history omission due to native input is reported without altering retained text`() {
        var status: ContextBudgetStatus? = null
        val messages = listOf(Message("assistant", "previous answer"), Message("user", "latest request"))
        val request =
            ChatRequestContext(
                messages,
                contextWindow = ContextWindow(configuredLimit = 100),
                additionalInputTokens = 60,
                onContextBudgeted = { status = it },
            )
        assertEquals(
            "latest request",
            budgeter
                .fit(request, messages)
                .messages
                .last()
                .content,
        )
        assertTrue(requireNotNull(status).historyReduced)
        assertFalse(requireNotNull(status).toolSchemasReduced)
    }

    private object LengthEstimator : TokenCountEstimator {
        override fun estimate(text: String?): Int = text?.length ?: 0

        override fun estimate(content: MediaContent): Int = estimate(content.text)

        override fun estimate(messages: Iterable<MediaContent>): Int = messages.sumOf { estimate(it) }
    }
}
