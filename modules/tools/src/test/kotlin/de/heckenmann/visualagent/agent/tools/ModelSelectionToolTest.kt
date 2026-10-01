package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.AgentModelSelection
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionPort
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionResult
import de.heckenmann.visualagent.agent.tools.api.ToolErrorCode
import de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelSelectionToolTest {
    @Test
    fun `native function dispatch forwards trusted caller and emits structured lifecycle results`() {
        val context = mapOf<String, Any>("agentId" to "worker")
        val tool =
            ModelSelectionTool(
                ModelSelectionPort { request, caller ->
                    assertEquals(context, caller.filterKeys { it == "agentId" })
                    assertEquals("set", request.action)
                    assertEquals("local", request.providerId)
                    assertEquals("new", request.modelId)
                    Mono.just(ModelSelectionResult(AgentModelSelection("worker", "local", "new")))
                },
            )
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        bus.addListener(events::add).use {
            val registry = ToolRegistry(listOf(tool), bus)
            assertEquals("model_selection", registry.toolDefinitions().single().name)
            val response =
                registry.executeBlocking(
                    tool,
                    """{"action":"set","providerId":"local","modelId":"new","timeoutSeconds":30,"async":false}""",
                    context,
                )
            val result = Json.decodeFromString<ToolResultEnvelope>(response)

            assertTrue(result.success)
            assertTrue(result.data.toString().contains("next_agent_request"))
            assertEquals(listOf(ToolCallPhase.STARTED, ToolCallPhase.FINISHED), events.map { it.phase })
        }
    }

    @Test
    fun `invalid input never reaches the selection port`() {
        var calls = 0
        val tool =
            ModelSelectionTool(
                ModelSelectionPort { _, _ ->
                    calls++
                    Mono.just(ModelSelectionResult(AgentModelSelection("main", "local", "current")))
                },
            )
        val registry = ToolRegistry(listOf(tool), ToolEventBus())
        listOf(
            """{"action":"set","modelId":"new","agentId":"main"}""",
            """{"action":"listModels","limit":51}""",
            """{"action":"listModels","offset":-1}""",
            """{"action":"listModels","limit":"invalid"}""",
            """{"action":"get","providerId":" "}""",
            "{}",
            "[]",
            "not-json",
        ).forEach { input ->
            val result = Json.decodeFromString<ToolResultEnvelope>(registry.executeBlocking(tool, input, mapOf("agent" to "main")))
            assertFalse(result.success, input)
            assertEquals(ToolErrorCode.INVALID_ARGUMENT, result.error?.code, input)
        }
        assertEquals(0, calls)
    }

    @Test
    fun `permission errors reach the model without leaking raw exception text`() {
        val tool = ModelSelectionTool(ModelSelectionPort { _, _ -> Mono.error(SecurityException("credential-secret")) })
        val registry = ToolRegistry(listOf(tool), ToolEventBus())
        val response = registry.executeBlocking(tool, """{"action":"get"}""", emptyMap())
        val result = Json.decodeFromString<ToolResultEnvelope>(response)
        assertFalse(result.success)
        assertEquals(ToolErrorCode.PERMISSION_DENIED, result.error?.code)
        assertFalse(response.contains("credential-secret"))
    }
}
