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
    fun `conversation history outranks regular schemas and retains the tool help fallback`() {
        val previousAnswer = "a".repeat(20)
        val request =
            ChatRequestContext(
                messages =
                    listOf(
                        Message("system", "rules"),
                        Message("user", "earlier question"),
                        Message("assistant", previousAnswer),
                        Message("user", "latest"),
                    ),
                parameters = ModelParameters(maxTokens = 20),
                contextWindow = ContextWindow(configuredLimit = 200),
            )
        val help = ToolDefinition(ToolId("tool:help"), "tool_help", "discover", "{}")
        val regular = ToolDefinition(ToolId("workspace:file"), "workspace_file", "x".repeat(100), "x".repeat(200))
        val fallbackGuard = Message("system", "tool_help guard", id = "__provider_tool_guard__")
        val fullGuard = Message("system", "full tool guard", id = "__provider_tool_guard__")

        val plan =
            budgeter.fitWithToolFallback(
                request = request,
                fallbackMessages = listOf(fallbackGuard) + request.messages,
                fallbackTools = listOf(help),
                fullMessages = listOf(fullGuard) + request.messages,
                fullTools = listOf(help, regular),
            )

        assertEquals(setOf("tool_help"), plan.toolNames)
        assertTrue(plan.request.messages.any { it.role == "assistant" && it.content == previousAnswer })
        assertTrue(plan.request.messages.any { it.role == "user" && it.content == "latest" })
        assertFalse(plan.status.historyReduced)
        assertTrue(plan.status.toolSchemasReduced)
    }

    @Test
    fun `regular tool schemas may use capacity after the latest user and ten priority answers`() {
        val request =
            ChatRequestContext(
                messages =
                    listOf(
                        Message("system", "rules"),
                        Message("user", "older question"),
                        Message("assistant", "recent answer"),
                        Message("user", "latest"),
                    ),
                parameters = ModelParameters(maxTokens = 20),
                contextWindow = ContextWindow(configuredLimit = 250),
            )
        val help = ToolDefinition(ToolId("tool:help"), "tool_help", "help", "{}")
        val regular = ToolDefinition(ToolId("workspace:file"), "workspace_file", "x".repeat(100), "{}")
        val fallbackGuard = Message("system", "tool_help guard", id = "__provider_tool_guard__")
        val fullGuard = Message("system", "tool_help workspace_file guard", id = "__provider_tool_guard__")

        val plan =
            budgeter.fitWithToolFallback(
                request = request,
                fallbackMessages = listOf(fallbackGuard) + request.messages,
                fallbackTools = listOf(help),
                fullMessages = listOf(fullGuard) + request.messages,
                fullTools = listOf(help, regular),
            )

        assertEquals(setOf("tool_help", "workspace_file"), plan.toolNames)
        assertTrue(plan.request.messages.any { it.content == "recent answer" })
        assertTrue(plan.request.messages.any { it.content == "latest" })
        assertFalse(plan.request.messages.any { it.content == "older question" })
        assertTrue(plan.status.historyReduced)
        assertFalse(plan.status.toolSchemasReduced)
    }

    @Test
    fun `prioritizes the latest ten assistant answers before lower-priority history`() {
        val messages =
            buildList {
                add(Message("system", "rules"))
                repeat(12) { index ->
                    add(Message("user", "question-$index"))
                    add(Message("assistant", "answer-$index"))
                }
                add(Message("assistant", "t".repeat(50), contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE))
                add(Message("user", "latest"))
            }

        val request =
            ChatRequestContext(
                messages,
                parameters = ModelParameters(maxTokens = 20),
                contextWindow = ContextWindow(configuredLimit = 400),
            )
        val result = budgeter.fit(request, messages)

        assertEquals(
            (2..11).map { "answer-$it" },
            result.messages
                .filter {
                    it.role == "assistant" && it.contextPolicy != ConversationContextPolicy.SUMMARY_SOURCE
                }.map { it.content },
        )
        assertTrue(result.messages.any { it.content == "t".repeat(50) })
        assertTrue(result.messages.any { it.content == "latest" })
        assertFalse(result.messages.any { it.content == "answer-1" })
    }

    @Test
    fun `preserves the latest assistant answer ahead of large tool history`() {
        val messages =
            listOf(
                Message("system", "rules"),
                Message("user", "previous question"),
                Message("assistant", "previous answer"),
                Message("assistant", "x".repeat(400), contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE),
                Message("user", "current question"),
            )
        val request =
            ChatRequestContext(
                messages,
                parameters = ModelParameters(maxTokens = 20),
                contextWindow = ContextWindow(configuredLimit = 150),
            )

        val result = budgeter.fit(request, messages)

        assertTrue(result.messages.any { it.content == "previous answer" })
        assertTrue(result.messages.any { it.content == "current question" })
        assertFalse(result.messages.any { it.content.contains("x".repeat(100)) })
    }

    @Test
    fun `fills remaining context with tool events and older dialogue after prioritized answers`() {
        val messages =
            listOf(
                Message("system", "rules"),
                Message("user", "older question"),
                Message("assistant", "older answer"),
                Message("assistant", "tool result", contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE),
                Message("user", "latest question"),
            )
        val request =
            ChatRequestContext(
                messages,
                parameters = ModelParameters(maxTokens = 20),
                contextWindow = ContextWindow(configuredLimit = 1_000),
            )

        val result = budgeter.fit(request, messages)

        assertEquals(messages, result.messages)
    }

    @Test
    fun `truncates the latest prior assistant answer instead of dropping it when it alone exceeds the remaining budget`() {
        val messages =
            listOf(
                Message("system", "rules"),
                Message("user", "prior question"),
                Message("assistant", "A".repeat(500)),
                Message("user", "latest"),
            )
        val request =
            ChatRequestContext(
                messages,
                parameters = ModelParameters(maxTokens = 20),
                contextWindow = ContextWindow(configuredLimit = 150),
            )

        val result = budgeter.fit(request, messages)
        val retainedAnswer = result.messages.first { it.role == "assistant" }

        assertTrue(retainedAnswer.content.startsWith("A"))
        assertTrue(retainedAnswer.content.endsWith("[Earlier answer truncated to fit the model context window.]"))
        assertTrue(result.messages.any { it.content == "latest" })
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
