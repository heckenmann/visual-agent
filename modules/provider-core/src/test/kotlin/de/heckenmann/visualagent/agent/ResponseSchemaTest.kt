package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import org.springframework.ai.tokenizer.TokenCountEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Verifies evidence-based schema selection and accounting independent of model names. */
class ResponseSchemaTest {
    private val schema = ResponseSchema("""{"type":"object"}""")
    private val request = ChatRequestContext(listOf(Message("user", "evaluate")), responseSchema = schema)

    @Test
    fun `unknown support never enables native output`() {
        assertNull(request.nativeResponseSchema())
        assertNull(request.copy(modelCapabilitiesComplete = true, modelCapabilities = setOf("tools", "vision")).nativeResponseSchema())
        assertNull(request.copy(model = "gpt-4o", provider = "openai").nativeResponseSchema())
    }

    @Test
    fun `positive support comes from explicit configuration capability or protocol`() {
        assertEquals(schema, request.copy(options = mapOf("structuredOutput.native" to "true")).nativeResponseSchema())
        assertEquals(schema, request.copy(modelCapabilities = setOf("structured_outputs")).nativeResponseSchema())
        assertEquals(schema, request.nativeResponseSchema(protocolSupportsSchema = true))
        assertNull(request.copy(options = mapOf("structuredOutput.native" to "false")).nativeResponseSchema(protocolSupportsSchema = true))
        assertNull(request.copy(responseSchema = null).nativeResponseSchema(protocolSupportsSchema = true))
    }

    @Test
    fun `staged profile options honor model and request overrides`() {
        val profile =
            ProviderProfile(
                "custom",
                "Custom",
                ProviderAdapter.OPENAI_COMPATIBLE,
                "https://example.invalid",
                options = mapOf("structuredOutput.native" to "true"),
                models = listOf(ProviderModelConfig("model", options = mapOf("structuredOutput.native" to "false"))),
            )
        val staged = request.copy(providerProfile = profile)
        assertEquals(schema, staged.nativeResponseSchema())
        assertNull(staged.copy(model = "model").nativeResponseSchema())
        assertEquals(schema, staged.copy(model = "model", options = mapOf("structuredOutput.native" to "true")).nativeResponseSchema())
    }

    @Test
    fun `schema tokens participate in the mandatory context budget`() {
        val estimator =
            object : TokenCountEstimator {
                override fun estimate(text: String?): Int = text?.length ?: 0

                override fun estimate(media: org.springframework.ai.content.MediaContent): Int = 0

                override fun estimate(media: Iterable<org.springframework.ai.content.MediaContent>): Int = 0
            }
        val budgeter = RequestContextBudgeter(estimator)
        val limited = request.copy(contextWindow = ContextWindow(configuredLimit = 45), parameters = ModelParameters(maxTokens = 5))
        assertFailsWith<ContextWindowExceededException> {
            budgeter.fit(limited.copy(responseSchema = ResponseSchema("""{"type":"object","description":"too large"}""")), limited.messages)
        }
        assertEquals(5, budgeter.fit(limited.copy(responseSchema = null), limited.messages).parameters.maxTokens)
    }
}
