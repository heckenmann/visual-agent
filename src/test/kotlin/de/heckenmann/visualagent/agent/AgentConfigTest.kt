package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentConfigTest {
    @Test
    fun `modelSelection drops blank overrides and maps parameters`() {
        val config =
            AgentConfig(
                timeout = 120,
                maxRetries = 5,
                memoryLimitMb = 1024,
                provider = "  ",
                model = "llama3",
                temperature = 0.7,
                topP = 0.9,
                maxTokens = 512,
                variant = "  ",
                options = mapOf("mirostat" to "1"),
            )

        val selection = config.modelSelection()

        assertNull(selection.provider)
        assertEquals("llama3", selection.model)
        assertNull(selection.variant)
        assertEquals(0.7, selection.parameters.temperature)
        assertEquals(0.9, selection.parameters.topP)
        assertEquals(512, selection.parameters.maxTokens)
        assertEquals(mapOf("mirostat" to "1"), selection.options)
    }

    @Test
    fun `template maps default config when unknown`() {
        val config = AgentConfig.fromTemplate("nonexistent")

        assertEquals(AgentConfig(), config)
    }

    @Test
    fun `template returns researcher config`() {
        val config = AgentConfig.fromTemplate("researcher")

        assertEquals(120, config.timeout)
        assertEquals(5, config.maxRetries)
        assertEquals(512, config.memoryLimitMb)
    }

    @Test
    fun `data class properties are readable`() {
        val config = AgentConfig(tools = listOf("context", "terminal"))

        assertEquals(60, config.timeout)
        assertEquals(3, config.maxRetries)
        assertEquals(512, config.memoryLimitMb)
        assertEquals(listOf("context", "terminal"), config.tools)
    }
}
