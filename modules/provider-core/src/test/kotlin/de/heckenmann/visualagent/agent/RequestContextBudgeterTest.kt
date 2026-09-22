package de.heckenmann.visualagent.agent

import org.springframework.ai.content.MediaContent
import org.springframework.ai.tokenizer.TokenCountEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies model-aware request budgeting and latest-user-message priority. */
class RequestContextBudgeterTest {
    private val budgeter = RequestContextBudgeter(LengthTokenEstimator)

    @Test
    fun `uses the smallest configured or model context window`() {
        assertEquals(512, ContextWindow(configuredLimit = 1_024, modelLimit = 512).effectiveLimit())
        assertEquals(1_024, ContextWindow(configuredLimit = 1_024).effectiveLimit())
    }

    @Test
    fun `retains the latest user message and drops older history first`() {
        val request =
            ChatRequestContext(
                messages =
                    listOf(
                        Message("system", "rules"),
                        Message("user", "old request that is no longer relevant"),
                        Message("assistant", "old answer"),
                        Message("user", "latest request"),
                    ),
                contextWindow = ContextWindow(configuredLimit = 55),
            )

        val result = budgeter.fit(request, request.messages)

        assertTrue(result.messages.any { it.content == "latest request" })
        assertFalse(result.messages.any { it.content == "old request that is no longer relevant" })
        assertFalse(result.messages.any { it.content == "old answer" })
        assertEquals("rules", result.messages.first().content)
    }

    @Test
    fun `retains repeated messages only when their complete turn fits`() {
        val repeated = "repeat"
        val request =
            ChatRequestContext(
                messages =
                    listOf(
                        Message("system", "rules"),
                        Message("user", repeated),
                        Message("assistant", repeated),
                        Message("user", repeated),
                    ),
                contextWindow = ContextWindow(configuredLimit = 45),
            )

        val result = budgeter.fit(request, request.messages)

        assertEquals(listOf("rules", repeated), result.messages.map(Message::content))
    }

    @Test
    fun `drops optional leading memory before the latest user turn`() {
        val request =
            ChatRequestContext(
                messages =
                    listOf(
                        Message("system", "policy"),
                        Message("system", "x".repeat(100), contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE),
                        Message("user", "latest"),
                    ),
                contextWindow = ContextWindow(configuredLimit = 70),
            )

        val result = budgeter.fit(request, request.messages)

        assertEquals(listOf("policy", "latest"), result.messages.map(Message::content))
    }

    @Test
    fun `retains fitting assistant reference context before the latest user turn`() {
        val request =
            ChatRequestContext(
                messages =
                    listOf(
                        Message("system", "policy"),
                        Message("assistant", "reference", contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE),
                        Message("user", "latest"),
                    ),
                contextWindow = ContextWindow(configuredLimit = 70),
            )

        val result = budgeter.fit(request, request.messages)

        assertEquals(listOf("policy", "reference", "latest"), result.messages.map(Message::content))
    }

    @Test
    fun `derives a positive output limit when model output metadata is unavailable`() {
        val request =
            ChatRequestContext(
                messages = listOf(Message("system", "rules"), Message("user", "latest")),
                contextWindow = ContextWindow(configuredLimit = 100),
            )

        val result = budgeter.fit(request, request.messages)

        assertEquals(25, result.parameters.maxTokens)
    }

    @Test
    fun `counts exact tool schemas and explicit output against the model window`() {
        val request =
            ChatRequestContext(
                messages = listOf(Message("system", "rules"), Message("user", "latest")),
                parameters = ModelParameters(maxTokens = 100),
                contextWindow = ContextWindow(configuredLimit = 100),
            )
        val tool = ToolDefinition(ToolId("workspace:file"), "workspace_file", "inspect", "schema")

        val result = budgeter.fit(request, request.messages, listOf(tool))

        assertTrue(result.parameters.maxTokens!! < 100)
        assertTrue(result.parameters.maxTokens > 0)
    }

    @Test
    fun `rejects a request when mandatory content cannot fit`() {
        val request =
            ChatRequestContext(
                messages = listOf(Message("system", "system instructions"), Message("user", "latest")),
                contextWindow = ContextWindow(configuredLimit = 5),
            )

        assertFailsWith<ContextWindowExceededException> {
            budgeter.fit(request, request.messages)
        }
    }

    private object LengthTokenEstimator : TokenCountEstimator {
        override fun estimate(text: String?): Int = text?.length ?: 0

        override fun estimate(mediaContent: MediaContent): Int = 0

        override fun estimate(mediaContents: Iterable<MediaContent>): Int = 0
    }
}
