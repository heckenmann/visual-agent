package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ProviderToolCall
import de.heckenmann.visualagent.agent.tools.api.ToolBatchSafety
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Proves identical tool names/arguments retain immutable distinct provider identities. */
class NativeToolBatchRoundTest {
    @Test
    fun `malformed batch JSON returns canonical errors before any tool starts`() {
        var started = false
        val tool =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("read"), "read", "Test", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult {
                    started = true
                    return success("read", "unexpected")
                }
            }
        val registry = ToolRegistry(listOf(tool), ToolEventBus())
        val results =
            NativeToolBatchRound(registry, ToolBatchExecutor(registry))
                .execute(
                    listOf(ProviderToolCall("first", "function", "read", "{}"), ProviderToolCall("bad", "function", "read", "{")),
                    0,
                    null,
                    setOf("read"),
                    emptyMap(),
                ).block(Duration.ofSeconds(5))!!
        assertTrue(!started)
        results.forEach {
            val result =
                kotlinx.serialization.json.Json.decodeFromString<
                    de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope,
                >(it)
            assertEquals(de.heckenmann.visualagent.agent.tools.api.ToolErrorCode.INVALID_ARGUMENT, result.error?.code)
        }
    }

    @Test
    fun `duplicate function names preserve call identity and parent history under overlap`() {
        val entered = CountDownLatch(2)
        val release = Sinks.empty<Void>()
        val identities = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val tool =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("read"), "read", "Test", "{}", ToolBatchSafety.READ_ONLY_PARALLEL)

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult = error("Reactive only")

                override fun executeReactive(
                    inputJson: String,
                    context: Map<String, Any>,
                ): Mono<ToolResult> {
                    identities[context.getValue("providerToolCallId").toString()] = context.getValue("toolCallSequence") as Int
                    assertEquals("parent", context["parentAssistantTurnId"])
                    assertEquals(2, context["toolCallRound"])
                    entered.countDown()
                    return release.asMono().thenReturn(success("read", "done"))
                }
            }
        val registry = ToolRegistry(listOf(tool), ToolEventBus())
        val calls = listOf(ProviderToolCall("first", "function", "read", "{}"), ProviderToolCall("second", "function", "read", "{}"))
        val future =
            NativeToolBatchRound(
                registry,
                ToolBatchExecutor(registry),
            ).execute(calls, 2, "parent", setOf("read"), emptyMap()).toFuture()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        release.tryEmitEmpty()
        assertEquals(2, future.get(5, TimeUnit.SECONDS)!!.size)
        assertEquals(mapOf("first" to 0, "second" to 1), identities)
    }

    @Test
    fun `native batch preserves timeout codes and successful siblings`() {
        val tool =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("read"), "read", "Test", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult =
                    if (context["providerToolCallId"] == "timeout") failure("read", "TOOL_TIMEOUT: Expired") else success("read", "done")
            }
        val registry = ToolRegistry(listOf(tool), ToolEventBus())
        val results =
            NativeToolBatchRound(registry, ToolBatchExecutor(registry))
                .execute(
                    listOf(ProviderToolCall("ok", "function", "read", "{}"), ProviderToolCall("timeout", "function", "read", "{}")),
                    0,
                    null,
                    setOf("read"),
                    emptyMap(),
                ).block(Duration.ofSeconds(5))!!
                .map {
                    kotlinx.serialization.json.Json.decodeFromString<
                        de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope,
                    >(it)
                }
        assertTrue(results[0].success)
        assertEquals(de.heckenmann.visualagent.agent.tools.api.ToolErrorCode.TIMEOUT, results[1].error?.code)
    }

    @Test
    fun `single native call keeps the complete existing registry envelope`() {
        val tool =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("read"), "read", "Test", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult = success("read", "full value")
            }
        val registry = ToolRegistry(listOf(tool), ToolEventBus())
        for (input in listOf("{}", "not-json", "[]")) {
            val direct = registry.executeBlocking(tool, input, emptyMap())
            val native =
                NativeToolBatchRound(
                    registry,
                    ToolBatchExecutor(registry),
                ).execute(
                    listOf(ProviderToolCall("id", "function", "read", input)),
                    0,
                    null,
                    setOf("read"),
                    emptyMap(),
                ).block(Duration.ofSeconds(5))!!
            assertEquals(listOf(direct), native)
        }
    }
}
