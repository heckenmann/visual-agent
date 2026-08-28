package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TerminalToolTest {
    @Test
    fun `registry timeout stops a running terminal process`() {
        ToolRegistry(listOf(TerminalTool()), ToolEventBus()) { 1 }.use { registry ->
            val terminal = registry.resolve(setOf(ToolId("terminal"))).single()

            val result = registry.execute(terminal, """{"command":"sleep 30"}""", emptyMap())
            val json = Json.parseToJsonElement(result).jsonObject

            assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
            assertContains(json["error"]!!.jsonPrimitive.content, "TOOL_TIMEOUT")
        }
    }
}
