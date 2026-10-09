package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.api.ToolBatchSafety
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.failure
import de.heckenmann.visualagent.agent.tools.success
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Exercises genuine guest Promises without evaluating guest callbacks on host worker threads. */
class JavaScriptBatchTest {
    @Test
    fun `callMany returns a Promise and retains ordered partial failure`() {
        withService { service, _ ->
            val result =
                service.execute(
                    request(
                        """
                const promise = tools.callMany([{id:'one',name:'read',arguments:{}},{id:'two',name:'fail',arguments:{}}]);
                if (!(promise instanceof Promise)) throw new Error('not a Promise');
                const results = await promise;
                if (results[0].toolId !== 'read' || results[0].data !== 'done' || results[0].error !== null) throw new Error('success envelope');
                if (!results[1].error.code || !results[1].error.remediation || typeof results[1].error.retryable !== 'boolean') throw new Error('error envelope');
                return results.map(item => ({id:item.id,success:item.success}));
            """,
                    ),
                )
            assertEquals(listOf(mapOf("id" to "one", "success" to true), mapOf("id" to "two", "success" to false)), result.value)
        }
    }

    @Test
    fun `malformed or unauthorized batch has no child side effects`() {
        withService { service, starts ->
            for (source in listOf(
                "return await tools.callMany([{id:'one',name:'read',arguments:{}},{id:'two',name:'disabled',arguments:{}}]);",
                "return await tools.callMany([{id:'same',name:'read',arguments:{}},{id:'same',name:'read',arguments:{}}]);",
                "return await tools.callMany([{id:'one',name:'read',arguments:()=>{}}]);",
                "return await tools.callMany([{id:'one',name:'read',arguments:[]}]);",
            )) {
                assertFailsWith<JavaScriptExecutionException> { service.execute(request(source)) }
                assertEquals(0, starts.get())
            }
        }
    }

    @Test
    fun `each batch child consumes the script call budget`() {
        withService { service, starts ->
            val failure =
                assertFailsWith<JavaScriptExecutionException> {
                    service.execute(
                        request(
                            "return await tools.callMany([{id:'one',name:'read',arguments:{}},{id:'two',name:'read',arguments:{}}]);",
                        ).copy(limits = JavaScriptExecutionLimits(maxToolCalls = 1)),
                    )
                }
            assertEquals(JavaScriptErrorCategory.LIMIT_EXCEEDED, failure.category)
            assertEquals(0, starts.get())
        }
    }

    @Test
    fun `guest continues while a batch is pending and cannot access host threads`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slow =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("slow"), "slow", "Test", "{}", ToolBatchSafety.READ_ONLY_PARALLEL)

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult {
                    entered.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                    return success("slow", "done")
                }
            }
        val registry = ToolRegistry(listOf(slow), ToolEventBus())
        GraalJavaScriptExecutionService({ registry }, JavaScriptWorkspaceWriter { _, _ -> error("Unused") }).use { service ->
            val pool = Executors.newSingleThreadExecutor()
            try {
                val result =
                    pool.submit<JavaScriptExecutionResult> {
                        service.execute(
                            JavaScriptExecutionRequest(
                                "const p = tools.callMany([{id:'one',name:'slow',arguments:{}}]); if (typeof Java !== 'undefined') throw new Error('host escape'); console.log('continued'); return await p;",
                                setOf("slow"),
                            ),
                        )
                    }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                release.countDown()
                assertEquals(
                    "continued",
                    result
                        .get(10, TimeUnit.SECONDS)
                        .logs
                        .single()
                        .message,
                )
            } finally {
                release.countDown()
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun `invalid child rejects promptly while an earlier guest batch call is pending`() {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val tools =
            listOf("slow", "invalid").map { id ->
                object : VisualAgentTool {
                    override val definition = ToolDefinition(ToolId(id), id, "Test", "{}", ToolBatchSafety.READ_ONLY_PARALLEL)

                    override fun execute(
                        inputJson: String,
                        context: Map<String, Any>,
                    ): ToolResult = error("Reactive only")

                    override fun executeReactive(
                        inputJson: String,
                        context: Map<String, Any>,
                    ): reactor.core.publisher.Mono<ToolResult> =
                        reactor.core.publisher.Mono.defer {
                            if (id == "slow") {
                                entered.countDown()
                                reactor.core.publisher.Mono
                                    .never<ToolResult>()
                                    .doOnCancel { cancelled.countDown() }
                            } else {
                                assertTrue(entered.await(5, TimeUnit.SECONDS))
                                reactor.core.publisher.Mono
                                    .just(failure(id, "TOOL_ARGUMENTS: Missing value"))
                            }
                        }
                }
            }
        val registry = ToolRegistry(tools, ToolEventBus())
        GraalJavaScriptExecutionService({ registry }, JavaScriptWorkspaceWriter { _, _ -> error("Unused") }).use { service ->
            val failure =
                assertFailsWith<JavaScriptExecutionException> {
                    service.execute(
                        JavaScriptExecutionRequest(
                            "return await tools.callMany([{id:'slow',name:'slow',arguments:{}},{id:'bad',name:'invalid',arguments:{}}]);",
                            setOf("slow", "invalid"),
                        ),
                    )
                }
            assertEquals(JavaScriptErrorCategory.TOOL_ARGUMENTS, failure.category)
            assertTrue(cancelled.await(5, TimeUnit.SECONDS))
        }
    }

    private fun request(source: String) = JavaScriptExecutionRequest(source, setOf("read", "fail"))

    private fun withService(block: (GraalJavaScriptExecutionService, AtomicInteger) -> Unit) {
        val starts = AtomicInteger()
        val tools =
            listOf("read", "fail").map { id ->
                object : VisualAgentTool {
                    override val definition = ToolDefinition(ToolId(id), id, "Test", "{}", ToolBatchSafety.READ_ONLY_PARALLEL)

                    override fun execute(
                        inputJson: String,
                        context: Map<String, Any>,
                    ): ToolResult {
                        starts.incrementAndGet()
                        return if (id ==
                            "read"
                        ) {
                            success(id, "done")
                        } else {
                            failure(id, "TOOL_EXECUTION: expected failure")
                        }
                    }
                }
            }
        val registry = ToolRegistry(tools, ToolEventBus())
        for (preferIsolate in listOf(false, true)) {
            starts.set(0)
            GraalJavaScriptExecutionService(
                { registry },
                JavaScriptWorkspaceWriter {
                    _,
                    _,
                    ->
                    error("Unused")
                },
                preferIsolate = preferIsolate,
            ).use { block(it, starts) }
        }
    }
}
