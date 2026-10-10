package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests the model batch entry point through the real registry boundary. */
class ToolBatchToolTest {
    @Test
    fun `malformed JSON is an invalid argument rather than a retryable execution failure`() {
        val registry = ToolRegistry(emptyList(), ToolEventBus())
        val batch = ToolBatchTool { ToolBatchExecutor(registry) }
        val result =
            Json.decodeFromString<ToolResultEnvelope>(
                registry.executeReactive(batch, "{", emptyMap()).block(Duration.ofSeconds(5))!!,
            )
        assertEquals(de.heckenmann.visualagent.agent.tools.api.ToolErrorCode.INVALID_ARGUMENT, result.error?.code)
        assertFalse(result.error!!.retryable)
    }

    @Test
    fun `argument failure preserves completed envelopes and skips remaining calls`() {
        val called = mutableListOf<String>()
        val child =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("read"), "read", "Test", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult {
                    called += inputJson
                    return if (inputJson.contains("invalid")) failure("read", "TOOL_ARGUMENTS: Missing value") else success("read", "done")
                }
            }
        val children = ToolRegistry(listOf(child), ToolEventBus())
        val batch = ToolBatchTool { ToolBatchExecutor(children) }
        val registry = ToolRegistry(listOf(child, batch), ToolEventBus())
        val result =
            Json.decodeFromString<ToolResultEnvelope>(
                registry
                    .executeReactive(
                        batch,
                        """{"calls":[{"id":"ok","tool":"read","arguments":{}},{"id":"bad","tool":"read","arguments":{"invalid":true}},{"id":"skip","tool":"read","arguments":{}}]}""",
                        mapOf("enabledTools" to setOf("read")),
                    ).block(Duration.ofSeconds(5))!!,
            )
        assertFalse(result.success)
        assertEquals(2, called.size)
        val items = result.data.jsonArray.map { it.jsonObject }
        val direct = Json.parseToJsonElement(children.executeBlocking(child, "{}", emptyMap())).jsonObject
        assertEquals(direct, JsonObject(items[0].filterKeys { it != "id" }))
        assertEquals(
            "INVALID_ARGUMENT",
            items[1]
                .getValue("error")
                .jsonObject
                .getValue("code")
                .jsonPrimitive.content,
        )
        assertEquals(
            "CANCELLED",
            items[2]
                .getValue("error")
                .jsonObject
                .getValue("code")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `canonical function names dispatch only enabled children`() {
        val ids = mutableListOf<String>()
        val child =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("read:item"), "read_item", "Read", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult {
                    ids += context.getValue("providerToolCallId").toString()
                    return success("read:item", "read")
                }
            }
        val childRegistry = ToolRegistry(listOf(child), ToolEventBus())
        val batch = ToolBatchTool { ToolBatchExecutor(childRegistry) }
        val registry = ToolRegistry(listOf(child, batch), ToolEventBus())
        val input = """{"calls":[{"id":"same","tool":"read_item","arguments":{}}]}"""
        val context = mapOf("enabledTools" to setOf("read:item", "tools:batch"))
        repeat(2) {
            val serialized = registry.executeReactive(batch, input, context).block(Duration.ofSeconds(5))!!
            val result = Json.decodeFromString<ToolResultEnvelope>(serialized)
            assertTrue(result.success)
            assertEquals(
                "same",
                result.data.jsonArray
                    .single()
                    .jsonObject
                    .getValue("id")
                    .jsonPrimitive.content,
            )
        }
        assertEquals(2, ids.distinct().size)
    }

    @Test
    fun `async parent is rejected before any child work`() {
        var called = false
        val child =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("read"), "read", "Read", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult {
                    called = true
                    return success("read", "done")
                }
            }
        val children = ToolRegistry(listOf(child), ToolEventBus())
        val batch = ToolBatchTool { ToolBatchExecutor(children) }
        val registry = ToolRegistry(listOf(child, batch), ToolEventBus())
        val result =
            Json.decodeFromString<ToolResultEnvelope>(
                registry
                    .executeReactive(
                        batch,
                        """{"async":true,"calls":[{"id":"id","tool":"read","arguments":{}}]}""",
                        mapOf("enabledTools" to setOf("read")),
                    ).block(Duration.ofSeconds(5))!!,
            )
        assertFalse(result.success)
        assertFalse(called)
    }

    @Test
    fun `function name aliases cannot bypass recursive composition checks`() {
        val recursive =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("javascript:execute"), "javascript_execute", "Execute", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult = error("Must not start")
            }
        val executor = ToolBatchExecutor(ToolRegistry(listOf(recursive), ToolEventBus()))
        val failure =
            kotlin.test.assertFailsWith<ToolBatchValidationException> {
                executor
                    .execute(
                        ToolBatchRequest(
                            listOf(ToolBatchItem("id", "javascript_execute", JsonObject(emptyMap()))),
                            setOf("javascript:execute"),
                        ),
                    ).block()
            }
        assertEquals("TOOL_ACCESS", failure.category)
    }
}
