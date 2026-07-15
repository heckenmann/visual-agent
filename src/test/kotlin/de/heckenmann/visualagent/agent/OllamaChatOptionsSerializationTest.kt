package de.heckenmann.visualagent.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.ai.ollama.api.OllamaChatOptions
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies how [OllamaChatOptions] serializes so we can prevent empty `tools` arrays.
 */
class OllamaChatOptionsSerializationTest {
    private val mapper = ObjectMapper()

    @Test
    fun `clean options without tool callbacks do not serialize an empty toolCallbacks array`() {
        val options = OllamaChatOptions.builder().model("m").build()
        val json = mapper.writeValueAsString(options)
        assertFalse(json.contains("\"toolCallbacks\":[]"), "Expected no empty toolCallbacks array in $json")
    }

    @Test
    fun `empty tool callbacks explicitly set still serialize toolCallbacks field`() {
        val options =
            OllamaChatOptions
                .builder()
                .model("m")
                .toolCallbacks(emptyList())
                .build()
        val json = mapper.writeValueAsString(options)
        assertTrue(json.contains("\"toolCallbacks\":[]"), "Expected empty toolCallbacks field in $json")
    }
}
