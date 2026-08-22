package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import org.junit.jupiter.api.AfterEach
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraalJavaScriptExecutionServiceTest {
    private val events = ToolEventBus()
    private val registry = ToolRegistry(listOf(EchoTool(), NumbersTool(), SlowTool()), events)
    private val service =
        GraalJavaScriptExecutionService(
            { registry },
            JavaScriptWorkspaceWriter { path, content ->
                JavaScriptWorkspaceWriteResult(path, content.length.toLong(), "text/plain")
            },
        )

    @AfterEach
    fun close() {
        service.close()
        registry.close()
    }

    @Test
    fun `returns primitive object array and null values`() {
        assertEquals("hello", execute("return 'hello';").value)
        assertEquals(2.0, execute("return {answer: 2};").value.let { (it as Map<*, *>) ["answer"] })
        assertEquals(listOf(1.0, 2.0), execute("return [1, 2];").value)
        assertEquals(null, execute("return null;").value)
    }

    @Test
    fun `does not impose a source size limit`() {
        val source = "/*${"x".repeat(120_000)}*/ return 'ok';"

        assertEquals("ok", execute(source).value)
    }

    @Test
    fun `supports markdown string assembly and bounded console`() {
        val result =
            execute(
                "console.log('building'); console.warn('0123456789abcdef'); return '# Report\\n\\nDone.';",
                limits = JavaScriptExecutionLimits(maxLogCharacters = 18),
            )

        assertEquals("# Report\n\nDone.", result.value)
        assertEquals(
            listOf(JavaScriptLogEntry("log", "building"), JavaScriptLogEntry("warn", "0123456789")),
            result.logs,
        )
    }

    @Test
    fun `calls only enabled tools and processes result locally`() {
        val result =
            execute(
                """
                const numbers = await tools.call('test:numbers', {});
                return numbers.filter(value => value > 1).map(value => value * 2);
                """.trimIndent(),
                enabled = setOf("test:numbers"),
            )

        assertEquals(listOf(4.0, 6.0), result.value)
    }

    @Test
    fun `passes derived values between multiple tool calls`() {
        val result =
            execute(
                """
                const numbers = await tools.call('test:numbers', {});
                const doubled = numbers.map(value => value * 2);
                return await tools.call('test:echo', {values: doubled});
                """.trimIndent(),
                enabled = setOf("test:numbers", "test:echo"),
            )

        assertEquals(mapOf("values" to listOf(2.0, 4.0, 6.0)), result.value)
    }

    @Test
    fun `discovers enabled tool metadata`() {
        val result =
            execute(
                """
                const description = tools.describe('test:numbers');
                return {count: tools.list().length, id: description.id, name: description.name};
                """.trimIndent(),
                enabled = setOf("test:numbers"),
            )

        assertEquals(
            mapOf("count" to 1.0, "id" to "test:numbers", "name" to "test_numbers"),
            result.value,
        )
    }

    @Test
    fun `nested calls retain normal lifecycle events`() {
        val observed = mutableListOf<String>()
        val registration = events.addListener { observed += "${it.toolId}:${it.phase}" }
        try {
            execute("return await tools.call('test:numbers', {});", enabled = setOf("test:numbers"))
        } finally {
            registration.close()
        }
        assertEquals(listOf("test:numbers:STARTED", "test:numbers:FINISHED"), observed)
    }

    @Test
    fun `model facing tool returns final markdown value`() {
        val tool = JavaScriptExecuteTool(service)
        val result =
            tool.execute(
                """{"source":"return '# generated';"}""",
                mapOf("enabledTools" to setOf("javascript:execute")),
            )

        assertTrue(result.success)
        assertEquals("# generated", result.content)
    }

    @Test
    fun `model facing tool returns actionable execution errors`() {
        val tool = JavaScriptExecuteTool(service)
        val result =
            tool.execute(
                """{"source":"throw new Error('fix the input');"}""",
                mapOf("enabledTools" to setOf("javascript:execute")),
            )

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("RUNTIME"))
        assertTrue(result.error.orEmpty().contains("fix the input"))
        assertFalse(result.error.orEmpty().contains("PolyglotException"))
    }

    @Test
    fun `model facing registry path reports the JavaScript timeout category`() {
        val tool = JavaScriptExecuteTool(service)
        val modelRegistry = ToolRegistry(listOf(tool), ToolEventBus()) { 1 }
        try {
            val result = modelRegistry.execute(tool, """{"source":"while (true) {}"}""", emptyMap())

            assertTrue(result.contains("\"success\":false"))
            assertTrue(result.contains("TIMEOUT"))
        } finally {
            modelRegistry.close()
        }
    }

    @Test
    fun `rejects disabled and recursive tools`() {
        assertFailsWith<JavaScriptExecutionException> {
            execute("await tools.call('test:echo', {});", enabled = emptySet())
        }.also { assertEquals(JavaScriptErrorCategory.TOOL_ACCESS, it.category) }
        assertFailsWith<JavaScriptExecutionException> {
            execute("await tools.call('javascript:execute', {});", enabled = setOf("javascript:execute"))
        }.also { assertEquals(JavaScriptErrorCategory.TOOL_ACCESS, it.category) }
    }

    @Test
    fun `allows scripts to handle tool failures`() {
        val result =
            execute(
                """
                try {
                    await tools.call('missing:tool', {});
                    return 'unexpected';
                } catch (error) {
                    return 'handled';
                }
                """.trimIndent(),
                enabled = emptySet(),
            )

        assertEquals("handled", result.value)
    }

    @Test
    fun `reports a later promise failure after a handled bridge failure`() {
        val error =
            assertFailsWith<JavaScriptExecutionException> {
                execute(
                    """
                    try { await tools.call('test:echo', 'not-an-object'); } catch (_) {}
                    return await Promise.reject(new Error('later failure'));
                    """.trimIndent(),
                    enabled = setOf("test:echo"),
                )
            }

        assertEquals(JavaScriptErrorCategory.RUNTIME, error.category)
        assertTrue(error.message.contains("later failure"))
        assertFalse(error.message.contains("Tool arguments"))
    }

    @Test
    fun `bounds workspace helper calls and output bytes`() {
        assertFailsWith<JavaScriptExecutionException> {
            execute(
                "workspace.write({path: 'one.txt', content: '123'}); workspace.write({path: 'two.txt', content: '456'});",
                limits = JavaScriptExecutionLimits(maxWorkspaceBytes = 5),
            )
        }.also { assertEquals(JavaScriptErrorCategory.LIMIT_EXCEEDED, it.category) }

        assertFailsWith<JavaScriptExecutionException> {
            execute(
                "workspace.write({path: 'large.txt', content: '123'});",
                limits = JavaScriptExecutionLimits(maxWorkspaceWriteBytes = 2),
            )
        }.also { assertEquals(JavaScriptErrorCategory.LIMIT_EXCEEDED, it.category) }

        assertFailsWith<JavaScriptExecutionException> {
            execute(
                "workspace.write({path: 'one.txt', content: '1'}); workspace.write({path: 'two.txt', content: '2'});",
                limits = JavaScriptExecutionLimits(maxToolCalls = 1),
            )
        }.also { assertEquals(JavaScriptErrorCategory.LIMIT_EXCEEDED, it.category) }
    }

    @Test
    fun `bounds result while traversing guest arrays`() {
        assertFailsWith<JavaScriptExecutionException> {
            execute(
                "return Array(10_000).fill('0123456789');",
                limits = JavaScriptExecutionLimits(maxResultCharacters = 100),
            )
        }.also { assertEquals(JavaScriptErrorCategory.LIMIT_EXCEEDED, it.category) }
    }

    @Test
    fun `rejects nested asynchronous tool calls`() {
        assertFailsWith<JavaScriptExecutionException> {
            execute(
                "await tools.call('test:echo', {async: true});",
                enabled = setOf("test:echo"),
            )
        }.also {
            assertEquals(JavaScriptErrorCategory.TOOL_ARGUMENTS, it.category)
            assertTrue(it.message.contains("must be awaited"))
        }
    }

    @Test
    fun `maps syntax runtime and resource-limit failures`() {
        assertFailsWith<JavaScriptExecutionException> {
            execute("return (")
        }.also { assertEquals(JavaScriptErrorCategory.SYNTAX, it.category) }
        assertFailsWith<JavaScriptExecutionException> {
            execute("throw new Error('boom');")
        }.also { assertEquals(JavaScriptErrorCategory.RUNTIME, it.category) }
        assertFailsWith<JavaScriptExecutionException> {
            execute(
                "await tools.call('test:numbers', {}); await tools.call('test:numbers', {});",
                enabled = setOf("test:numbers"),
                limits = JavaScriptExecutionLimits(maxToolCalls = 1),
            )
        }.also { assertEquals(JavaScriptErrorCategory.LIMIT_EXCEEDED, it.category) }
        assertFailsWith<JavaScriptExecutionException> {
            execute("return 'x'.repeat(20);", limits = JavaScriptExecutionLimits(maxResultCharacters = 10))
        }.also { assertEquals(JavaScriptErrorCategory.LIMIT_EXCEEDED, it.category) }
        assertFailsWith<JavaScriptExecutionException> {
            execute("while (true) {}", limits = JavaScriptExecutionLimits(timeoutMillis = 100))
        }.also { assertEquals(JavaScriptErrorCategory.TIMEOUT, it.category) }
        assertFailsWith<JavaScriptExecutionException> {
            execute(
                "await tools.call('test:echo', 'not-an-object');",
                enabled = setOf("test:echo"),
            )
        }.also { assertEquals(JavaScriptErrorCategory.TOOL_ARGUMENTS, it.category) }
    }

    @Test
    fun `parent cancellation terminates execution`() {
        val token = CancellationToken()
        val thread =
            Thread {
                assertFailsWith<JavaScriptExecutionException> {
                    execute(
                        "while (true) {}",
                        token = token,
                        limits = JavaScriptExecutionLimits(timeoutMillis = 30_000),
                    )
                }.also { assertTrue(it.category == JavaScriptErrorCategory.CANCELLED || it.category == JavaScriptErrorCategory.RUNTIME) }
            }
        thread.start()
        Thread.sleep(100)
        token.cancel()
        thread.join(5_000)
        assertTrue(!thread.isAlive)
    }

    @Test
    fun `parent cancellation interrupts an in-flight tool call`() {
        val token = CancellationToken()
        val thread =
            Thread {
                assertFailsWith<JavaScriptExecutionException> {
                    execute(
                        "await tools.call('test:slow', {}); return 'done';",
                        enabled = setOf("test:slow"),
                        token = token,
                        limits = JavaScriptExecutionLimits(timeoutMillis = 30_000),
                    )
                }.also { assertEquals(JavaScriptErrorCategory.CANCELLED, it.category) }
            }
        thread.start()
        assertTrue(SlowTool.started.await(5, TimeUnit.SECONDS))
        token.cancel()
        thread.join(5_000)
        assertTrue(!thread.isAlive)
        assertTrue(SlowTool.interrupted.get())
    }

    private fun execute(
        source: String,
        enabled: Set<String> = emptySet(),
        token: CancellationToken? = null,
        limits: JavaScriptExecutionLimits = JavaScriptExecutionLimits(),
    ): JavaScriptExecutionResult =
        service.execute(
            JavaScriptExecutionRequest(
                source = source,
                enabledTools = enabled,
                cancellationToken = token,
                limits = limits,
            ),
        )

    private class EchoTool : VisualAgentTool {
        override val definition =
            ToolDefinition(
                ToolId("test:echo"),
                "test_echo",
                "Echo",
                "{\"type\":\"object\"}",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = ToolResult(definition.id.value, true, inputJson)
    }

    private class NumbersTool : VisualAgentTool {
        override val definition =
            ToolDefinition(
                ToolId("test:numbers"),
                "test_numbers",
                "Numbers",
                "{\"type\":\"object\"}",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = ToolResult(definition.id.value, true, "[1,2,3]")
    }

    private class SlowTool : VisualAgentTool {
        override val definition =
            ToolDefinition(
                ToolId("test:slow"),
                "test_slow",
                "Slow tool",
                "{\"type\":\"object\"}",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult {
            started.countDown()
            try {
                Thread.sleep(30_000)
            } catch (_: InterruptedException) {
                interrupted.set(true)
                Thread.currentThread().interrupt()
            }
            return ToolResult(definition.id.value, true, "done")
        }

        companion object {
            val started = CountDownLatch(1)
            val interrupted = AtomicBoolean(false)
        }
    }
}
