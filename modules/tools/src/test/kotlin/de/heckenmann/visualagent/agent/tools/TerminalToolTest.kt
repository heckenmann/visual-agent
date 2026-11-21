package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TerminalToolTest {
    @Test
    fun `registry timeout stops a running terminal process`() {
        val registry = ToolRegistry(listOf(TerminalTool { _, _ -> SleepingExecution() }), ToolEventBus()) { 1 }
        val terminal = registry.resolve(setOf(ToolId("terminal"))).single()

        val result = registry.executeBlocking(terminal, """{"target":"server","command":"sleep 30"}""", emptyMap())
        val json = Json.parseToJsonElement(result).jsonObject

        assertFalse(json["success"]!!.jsonPrimitive.content.toBoolean())
        assertContains(json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content, "TIMEOUT")
    }

    @Test
    fun `terminal forwards the explicit client target without falling back`() {
        val requests = mutableListOf<TerminalSandboxRequest>()
        val terminal =
            TerminalTool { request, _ ->
                requests.add(request)
                null
            }
        val result =
            terminal.execute(
                """{"target":"client","command":"pwd","grantId":"project","workingDirectory":"src"}""",
                emptyMap(),
            )
        assertFalse(result.success)
        assertEquals(listOf(TerminalSandboxRequest(TerminalHost.CLIENT, "pwd", "project", "src")), requests)
    }

    private class SleepingExecution : TerminalSandboxExecution {
        private val process = ProcessBuilder("sleep", "30").start()
        override val output = process.inputStream

        override fun awaitExit(): Int = process.waitFor()

        override fun cancel() {
            process.destroyForcibly()
        }
    }
}
