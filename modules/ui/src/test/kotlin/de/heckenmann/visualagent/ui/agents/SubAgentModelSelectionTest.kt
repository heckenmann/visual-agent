package de.heckenmann.visualagent.ui.agents

import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Keeps the sub-agent picker backward compatible while excluding explicitly non-chat models. */
class SubAgentModelSelectionTest {
    @Test
    fun `unknown model capabilities do not alter picker labels`() {
        val port = mockk<ProviderPort>()
        every { port.selectableModels("local") } returns listOf(ProviderModel("llama-2"))
        val options = subAgentModelOptions(port, "local", "")

        assertTrue(options.any { it.value == "llama-2" && it.label == "llama-2" })
    }

    @Test
    fun `non-chat models are excluded while custom selections remain visible`() {
        val port = mockk<ProviderPort>()
        every { port.selectableModels("local") } returns
            listOf(
                ProviderModel("image-only", capabilities = setOf("vision"), capabilitiesComplete = true),
                ProviderModel("chat", capabilities = setOf("chat"), capabilitiesComplete = true),
            )
        val options = subAgentModelOptions(port, "local", "custom")
        assertFalse(options.any { it.value == "image-only" })
        assertTrue(options.any { it.value == "chat" })
        assertTrue(options.any { it.value == "custom" })
        assertEquals(INHERIT_SELECTION, options.first().value)
    }
}
