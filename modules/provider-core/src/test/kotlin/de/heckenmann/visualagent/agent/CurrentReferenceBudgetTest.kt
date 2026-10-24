package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import org.springframework.ai.content.MediaContent
import org.springframework.ai.tokenizer.TokenCountEstimator
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Late current snapshots must remain optional and cannot displace recent dialogue. */
class CurrentReferenceBudgetTest {
    @Test
    fun `late reference snapshots retain their optional priority and chronological position`() {
        val policy = Message("system", "policy")
        val user = Message("user", "question")
        val answer = Message("assistant", "answer")
        val reference = Message("assistant", "x".repeat(500), contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE)
        val current = Message("user", "latest")
        val messages = listOf(policy, user, answer, reference, current)
        listOf(120, 2000).forEach { limit ->
            var status: ContextBudgetStatus? = null
            val request =
                ChatRequestContext(
                    messages,
                    parameters = ModelParameters(maxTokens = 10),
                    contextWindow = ContextWindow(configuredLimit = limit),
                    onContextBudgeted = { status = it },
                )
            val fitted = RequestContextBudgeter(LengthEstimator).fit(request, messages)
            assertEquals(if (limit == 120) listOf(policy, user, answer, current) else messages, fitted.messages)
            assertEquals(limit == 120, status?.historyReduced)
            assertTrue(fitted.messages.last() === current)
            assertEquals(reference, messages[3])
        }
    }

    private object LengthEstimator : TokenCountEstimator {
        override fun estimate(text: String?): Int = text?.length ?: 0

        override fun estimate(mediaContent: MediaContent): Int = 0

        override fun estimate(mediaContents: Iterable<MediaContent>): Int = 0
    }
}
