package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ModelParameters
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DirectResponsesOptionsTest {
    @Test
    fun `OAuth does not send the internal output budget as an unsupported API field`() {
        val request = ChatRequestContext(emptyList(), model = "selected", parameters = ModelParameters(maxTokens = 512))

        val options = directResponsesOptions(request, DirectProviderAuthMode.CHATGPT_OAUTH, emptyList())

        assertEquals("selected", options.model)
        assertNull(options.maxOutputTokens)
        assertNull(options.temperature)
    }

    @Test
    fun `API key route preserves supported output limits`() {
        val request = ChatRequestContext(emptyList(), model = "selected", parameters = ModelParameters(maxTokens = 512))

        val options = directResponsesOptions(request, DirectProviderAuthMode.API_KEY, emptyList())

        assertEquals(512, options.maxOutputTokens)
    }

    @Test
    fun `OAuth rejects unsupported sampling instead of changing the billing route`() {
        val request = ChatRequestContext(emptyList(), model = "selected", parameters = ModelParameters(temperature = 0.5))

        assertFailsWith<IllegalArgumentException> {
            directResponsesOptions(request, DirectProviderAuthMode.CHATGPT_OAUTH, emptyList())
        }
    }

    @Test
    fun `unknown hosted stateful and misspelled options are never silently ignored`() {
        listOf("hostedTools", "seed", "previous_response_id", "background", "temperaturee", "arbitrary").forEach { key ->
            DirectProviderAuthMode.entries.forEach { mode ->
                assertFailsWith<IllegalArgumentException> {
                    directResponsesOptions(
                        ChatRequestContext(emptyList(), model = "selected", options = mapOf(key to "value")),
                        mode,
                        emptyList(),
                    )
                }
            }
        }
    }

    @Test
    fun `shared native reasoning and tool options are explicitly mapped in both routes`() {
        DirectProviderAuthMode.entries.forEach { mode ->
            val request =
                ChatRequestContext(
                    emptyList(),
                    model = "selected",
                    options =
                        mapOf(
                            "reasoning_effort" to "high",
                            "reasoning_summary" to "auto",
                            "verbosity" to "low",
                            "parallel_tool_calls" to "false",
                            "tool_choice" to "none",
                            "service_tier" to "auto",
                            "strict" to "false",
                        ),
                )
            val options = directResponsesOptions(request, mode, emptyList())

            assertEquals("high", options.reasoningEffort)
            assertEquals("auto", options.reasoningSummary)
            assertEquals("low", options.verbosity)
            assertEquals(false, options.parallelToolCalls)
            assertEquals("none", options.toolChoice)
            assertEquals("auto", options.serviceTier)
            assertEquals(false, options.strict)
        }
    }

    @Test
    fun `API key sampling aliases are mapped and OAuth rejects the same values`() {
        val request =
            ChatRequestContext(
                emptyList(),
                model = "selected",
                options =
                    mapOf(
                        "top_p" to "0.9",
                        "temperature" to "0.5",
                        "max_tokens" to "128",
                    ),
            )
        val options = directResponsesOptions(request, DirectProviderAuthMode.API_KEY, emptyList())
        assertEquals(0.9, options.topP)
        assertEquals(0.5, options.temperature)
        assertEquals(128, options.maxOutputTokens)
        assertFailsWith<IllegalArgumentException> { directResponsesOptions(request, DirectProviderAuthMode.CHATGPT_OAUTH, emptyList()) }
    }

    @Test
    fun `conflicting aliases malformed booleans and impossible required tool choices fail locally`() {
        listOf(
            mapOf("reasoningEffort" to "low", "reasoning_effort" to "high"),
            mapOf("parallelToolCalls" to "yes"),
            mapOf("toolChoice" to "required"),
            mapOf("toolChoice" to "invented_function"),
        ).forEach { values ->
            assertFailsWith<IllegalArgumentException> {
                directResponsesOptions(
                    ChatRequestContext(emptyList(), model = "selected", options = values),
                    DirectProviderAuthMode.API_KEY,
                    emptyList(),
                )
            }
        }
    }
}
