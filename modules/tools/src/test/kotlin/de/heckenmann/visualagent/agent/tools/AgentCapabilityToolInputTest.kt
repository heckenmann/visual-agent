package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolAgentConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AgentCapabilityToolInputTest {
    @Test
    fun `agent capability tool input accepts provider-qualified model assignments`() {
        val input =
            Json
                .parseToJsonElement(
                    """{"enabledCapabilities":["CHAT","VISION"],"capabilityModels":{"VISION":"openai::gpt-4o"}}""",
                ).jsonObject

        val config = applyCapabilityToolInput(ToolAgentConfig(), input)

        assertEquals(setOf("CHAT", "VISION"), config.enabledCapabilities)
        assertEquals(mapOf("VISION" to "openai::gpt-4o"), config.capabilityModels)
    }

    @Test
    fun `agent capability tool input cannot disable chat or assign disabled capabilities`() {
        val noChat = Json.parseToJsonElement("""{"enabledCapabilities":["VISION"]}""").jsonObject
        val disabled = Json.parseToJsonElement("""{"capabilityModels":{"VISION":"gpt-4o"}}""").jsonObject

        assertFailsWith<IllegalArgumentException> { applyCapabilityToolInput(ToolAgentConfig(), noChat) }
        assertFailsWith<IllegalArgumentException> { applyCapabilityToolInput(ToolAgentConfig(), disabled) }
    }

    @Test
    fun `disabling capability removes its inherited model assignment`() {
        val original =
            ToolAgentConfig(
                enabledCapabilities = setOf("CHAT", "VISION"),
                capabilityModels = mapOf("VISION" to "openai::gpt-4o"),
            )
        val input = Json.parseToJsonElement("""{"enabledCapabilities":["CHAT"]}""").jsonObject

        val updated = applyCapabilityToolInput(original, input)

        assertEquals(setOf("CHAT"), updated.enabledCapabilities)
        assertEquals(emptyMap(), updated.capabilityModels)
    }

    @Test
    fun `explicit disabled capability assignment remains invalid`() {
        val original =
            ToolAgentConfig(
                enabledCapabilities = setOf("CHAT", "VISION"),
                capabilityModels = mapOf("VISION" to "openai::gpt-4o"),
            )
        val input =
            Json
                .parseToJsonElement(
                    """{"enabledCapabilities":["CHAT"],"capabilityModels":{"VISION":"openai::gpt-4o"}}""",
                ).jsonObject

        assertFailsWith<IllegalArgumentException> { applyCapabilityToolInput(original, input) }
    }

    @Test
    fun `agent capability tool input rejects unknown and malformed fields`() {
        val unknown = Json.parseToJsonElement("""{"enabledCapabilities":["CHAT","INVISIBLE"]}""").jsonObject
        val malformed = Json.parseToJsonElement("""{"capabilityModels":{"VISION":12}}""").jsonObject

        assertFailsWith<IllegalArgumentException> { applyCapabilityToolInput(ToolAgentConfig(), unknown) }
        assertFailsWith<IllegalArgumentException> { applyCapabilityToolInput(ToolAgentConfig(), malformed) }
    }
}
