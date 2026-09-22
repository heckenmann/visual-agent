package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Verifies that response framing is normalized independently of a concrete provider or model. */
class ProviderResponseContentNormalizerTest {
    @Test
    fun `removes standard leading assistant role markers`() {
        assertEquals("Answer", ProviderResponseContentNormalizer.normalize("assistant: Answer"))
        assertEquals("Answer", ProviderResponseContentNormalizer.normalize("<|assistant|> Answer"))
        assertEquals("Answer", ProviderResponseContentNormalizer.normalize("assistantAnswer"))
        assertEquals("Hallo", ProviderResponseContentNormalizer.normalize("assistantHallo"))
    }

    @Test
    fun `preserves ordinary assistant-prefixed words`() {
        assertEquals("assistantship", ProviderResponseContentNormalizer.normalize("assistantship"))
        assertEquals(
            "Assistant managers coordinate work",
            ProviderResponseContentNormalizer.normalize("Assistant managers coordinate work"),
        )
        assertEquals("assistantanswer", ProviderResponseContentNormalizer.normalize("assistantanswer"))
    }
}
