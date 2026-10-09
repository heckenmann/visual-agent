package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolBatchSafety
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gates actual subscriptions to prove conservative scheduling, correlation and resource cleanup. */
class ToolBatchExecutorTest {
    @Test
    fun `read children overlap but results and correlation retain declaration order`() {
        val gates = listOf(Sinks.one<ToolResult>(), Sinks.one<ToolResult>())
        val entered = CountDownLatch(2)
        val events = mutableListOf<ToolCallEvent>()
        val bus = ToolEventBus()
        val handle = bus.addListener { synchronized(events) { events += it } }
        val tools =
            gates.mapIndexed {
                index,
                gate,
                ->
                tool("read$index", ToolBatchSafety.READ_ONLY_PARALLEL) {
                    entered.countDown()
                    gate.asMono()
                }
            }
        val executor = ToolBatchExecutor(ToolRegistry(tools, bus))
        val result = executor.execute(request(tools).copy(context = mapOf("batchProviderCalls" to true))).toFuture()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        gates[1].tryEmitValue(success("read1", "second"))
        assertFalse(result.isDone)
        gates[0].tryEmitValue(success("read0", "first"))
        assertEquals(listOf("0", "1"), result.get(5, TimeUnit.SECONDS)!!.map { it.id })
        synchronized(events) {
            assertTrue(events.all { it.context["providerToolCallId"] == if (it.toolId == "read0") "0" else "1" })
            assertEquals(4, events.size)
        }
        handle.close()
    }

    @Test
    fun `unknown and exclusive tools form barriers between read groups`() {
        val entered = CountDownLatch(2)
        val gate = Sinks.one<ToolResult>()
        val mutationStarted = AtomicInteger()
        val tools =
            listOf(
                tool("first", ToolBatchSafety.READ_ONLY_PARALLEL) {
                    entered.countDown()
                    gate.asMono()
                },
                tool("second", ToolBatchSafety.READ_ONLY_PARALLEL) {
                    entered.countDown()
                    gate.asMono()
                },
                tool("mutate") {
                    mutationStarted.incrementAndGet()
                    Mono.just(success("mutate", "ok"))
                },
                tool("exclusive", ToolBatchSafety.EXCLUSIVE) {
                    assertEquals(1, mutationStarted.get())
                    Mono.just(success("exclusive", "ok"))
                },
            )
        val future = ToolBatchExecutor(ToolRegistry(tools, ToolEventBus())).execute(request(tools)).toFuture()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertEquals(0, mutationStarted.get())
        gate.tryEmitValue(success("read", "done"))
        assertEquals(4, future.get(5, TimeUnit.SECONDS)!!.size)
    }

    @Test
    fun `invalid sibling prevents every earlier mutation`() {
        val starts = AtomicInteger()
        val tools =
            listOf(
                tool("mutate") {
                    starts.incrementAndGet()
                    Mono.just(success("mutate", "ok"))
                },
            )
        val executor = ToolBatchExecutor(ToolRegistry(tools, ToolEventBus()))
        val valid = request(tools)
        for (items in listOf(
            valid.items + ToolBatchItem("bad", "disabled", JsonObject(emptyMap())),
            valid.items + valid.items.first(),
            valid.items.map { it.copy(tool = "javascript:execute") },
        )) {
            StepVerifier.create(executor.execute(valid.copy(items = items))).expectError(ToolBatchValidationException::class.java).verify()
        }
        assertEquals(0, starts.get())
    }

    @Test
    fun `child failure is data and never erases completed siblings`() {
        val tools = listOf(tool("ok") { Mono.just(success("ok", "done")) }, tool("fail") { Mono.error(IllegalStateException("secret")) })
        val outcomes = ToolBatchExecutor(ToolRegistry(tools, ToolEventBus())).execute(request(tools)).block()!!
        assertEquals(listOf(true, false), outcomes.map { it.success })
        assertFalse(Json.encodeToString(outcomes).contains("secret"))
    }

    @Test
    fun `parent cancellation prevents queued execution and releases global admission`() {
        val entered = CountDownLatch(1)
        val active = AtomicInteger()
        val queued = AtomicInteger()
        val tools =
            listOf(
                tool("first") {
                    active.incrementAndGet()
                    entered.countDown()
                    Mono.never()
                },
                tool("second") {
                    queued.incrementAndGet()
                    Mono.just(success("second", "ok"))
                },
            )
        val executor = ToolBatchExecutor(ToolRegistry(tools, ToolEventBus()), 1)
        val token = ToolCancellationToken()
        val subscription =
            executor
                .execute(
                    request(tools).copy(
                        context =
                            mapOf(
                                "toolCancellationRegistrar" to ToolCancellationRegistrar(token::onCancelled),
                            ),
                    ),
                ).subscribe({}, {})
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        token.cancel()
        subscription.dispose()
        assertEquals(0, queued.get())
        val result = executor.execute(request(listOf(tools[1]))).block(Duration.ofSeconds(5))!!
        assertTrue(result.single().success)
    }

    @Test
    fun `already cancelled parent launches nothing`() {
        val starts = AtomicInteger()
        val tools =
            listOf(
                tool("read") {
                    starts.incrementAndGet()
                    Mono.just(success("read", "ok"))
                },
            )
        val token = ToolCancellationToken().apply { cancel() }
        StepVerifier
            .create(
                ToolBatchExecutor(ToolRegistry(tools, ToolEventBus())).execute(
                    request(tools).copy(
                        context =
                            mapOf(
                                "batchProviderCalls" to true,
                                "toolCancellationRegistrar" to ToolCancellationRegistrar(token::onCancelled),
                            ),
                    ),
                ),
            ).assertNext { assertEquals(ToolBatchStatus.CANCELLED, it.single().status) }
            .verifyComplete()
        assertEquals(0, starts.get())
    }

    @Test
    fun `deadline cancels in flight and output budget covers escaped aggregate results`() {
        val tools = listOf(tool("slow") { Mono.never() })
        val executor = ToolBatchExecutor(ToolRegistry(tools, ToolEventBus()))
        StepVerifier
            .create(executor.execute(request(tools).copy(limits = ToolBatchLimits(timeoutMillis = 20))))
            .assertNext { assertFalse(it.single().success) }
            .verifyComplete()
        val outputTools = listOf(tool("large") { Mono.just(success("large", "\n".repeat(100000))) })
        val output =
            ToolBatchExecutor(
                ToolRegistry(outputTools, ToolEventBus()),
            ).execute(request(outputTools).copy(limits = ToolBatchLimits(maxResultCharacters = 1024))).block()!!
        assertTrue(Json.encodeToString(output).length <= 1024)
        val escapedIdentity =
            request(outputTools).copy(
                items = listOf(ToolBatchItem("\u0000".repeat(128), "large", JsonObject(emptyMap()))),
                limits = ToolBatchLimits(maxResultCharacters = 2048),
            )
        val escaped = ToolBatchExecutor(ToolRegistry(outputTools, ToolEventBus())).execute(escapedIdentity).block()!!
        assertTrue(Json.encodeToString(escaped).length <= 2048)
    }

    @Test
    fun `simultaneous batches respect global and shared request work budgets`() {
        for (sharedRequest in listOf(false, true)) {
            val entered = CountDownLatch(2)
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val gate = Sinks.one<ToolResult>()
            val tools =
                (0..3).map { index ->
                    tool("read$index", ToolBatchSafety.READ_ONLY_PARALLEL) {
                        peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                        entered.countDown()
                        gate.asMono().doOnNext { active.decrementAndGet() }
                    }
                }
            val executor = ToolBatchExecutor(ToolRegistry(tools, ToolEventBus()), if (sharedRequest) 16 else 2)
            val context = if (sharedRequest) mapOf("batchAdmission" to java.util.concurrent.Semaphore(2, true)) else emptyMap()
            val request = request(tools).copy(context = context, limits = ToolBatchLimits(maxConcurrency = if (sharedRequest) 2 else 4))
            val first = executor.execute(request).toFuture()
            val second = executor.execute(request.copy(batchId = "second")).toFuture()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            gate.tryEmitValue(success("read", "done"))
            assertEquals(4, first.get(5, TimeUnit.SECONDS)!!.size)
            assertEquals(4, second.get(5, TimeUnit.SECONDS)!!.size)
            assertTrue(peak.get() <= 2)
            assertEquals(0, active.get())
        }
    }

    private fun request(tools: List<VisualAgentTool>) =
        ToolBatchRequest(
            tools.mapIndexed { index, tool ->
                ToolBatchItem(
                    index.toString(),
                    tool.definition.id.value,
                    JsonObject(emptyMap()),
                )
            },
            tools
                .map {
                    it.definition.id.value
                }.toSet(),
        )

    private fun tool(
        id: String,
        safety: ToolBatchSafety = ToolBatchSafety.SEQUENTIAL_ONLY,
        work: () -> Mono<ToolResult>,
    ): VisualAgentTool =
        object : VisualAgentTool {
            override val definition = ToolDefinition(ToolId(id), id, "Test", "{}", safety)

            override fun execute(
                inputJson: String,
                context: Map<String, Any>,
            ): ToolResult = error("Reactive only")

            override fun executeReactive(
                inputJson: String,
                context: Map<String, Any>,
            ): Mono<ToolResult> = Mono.defer { work() }
        }
}
