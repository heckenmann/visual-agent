package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolBatchSafety
import de.heckenmann.visualagent.agent.tools.api.ToolErrorCode
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.Semaphore

/** Sole batch scheduler; conservative barriers and shared permits protect actual tool work. */
class ToolBatchExecutor(
    private val registry: ToolRegistry,
    globalConcurrency: Int = 16,
) {
    private val permits = Semaphore(globalConcurrency.also { require(it > 0) }, true)

    /** Validates all entries before side effects and collects ordered independent outcomes. */
    fun execute(request: ToolBatchRequest): Mono<List<ToolBatchOutcome>> =
        Mono.defer {
            val tools = validate(request)
            val deadline =
                minOf(
                    request.context["toolDeadlineNanos"] as? Long ?: Long.MAX_VALUE,
                    System.nanoTime() +
                        java.util.concurrent.TimeUnit.MILLISECONDS
                            .toNanos(request.limits.timeoutMillis),
                )
            val admission = request.context["batchAdmission"] as? Semaphore ?: Semaphore(request.limits.maxConcurrency, true)
            val outcomes =
                java.util.concurrent.atomic
                    .AtomicReferenceArray<ToolBatchOutcome>(request.items.size)
            val started =
                java.util.concurrent.atomic
                    .AtomicIntegerArray(request.items.size)
            val groups = mutableListOf<MutableList<Int>>()
            tools.indices.forEach { index ->
                if (tools[index].definition.batchSafety == ToolBatchSafety.READ_ONLY_PARALLEL &&
                    groups.lastOrNull()?.lastOrNull()?.let { tools[it].definition.batchSafety == ToolBatchSafety.READ_ONLY_PARALLEL } ==
                    true
                ) {
                    groups.last().add(index)
                } else {
                    groups.add(mutableListOf(index))
                }
            }
            val work =
                Flux
                    .fromIterable(groups)
                    .concatMap { group ->
                        Flux.fromIterable(group).flatMapSequential(
                            { index ->
                                executeChild(request, tools[index], index, deadline, admission, started).doOnNext {
                                    outcomes.set(index, it)
                                    if (it.errorCode == ToolErrorCode.INVALID_ARGUMENT) throw ToolBatchSyntaxFailure()
                                }
                            },
                            request.limits.maxConcurrency,
                            1,
                        )
                    }.collectList()
            val cancellation =
                Mono.create<List<ToolBatchOutcome>> { sink ->
                    val registration =
                        (request.context["toolCancellationRegistrar"] as? ToolCancellationRegistrar)
                            ?.register { sink.error(CancellationException("Tool batch cancelled")) }
                    sink.onDispose { registration?.close() }
                }
            Mono
                .firstWithSignal(cancellation, work)
                .timeout(Duration.ofNanos((deadline - System.nanoTime()).coerceAtLeast(1)))
                .onErrorResume { error ->
                    if (error is CancellationException ||
                        error is java.util.concurrent.TimeoutException ||
                        error is ToolBatchSyntaxFailure
                    ) {
                        Mono.just(
                            request.items.mapIndexed { index, item ->
                                outcomes.get(index) ?: ToolBatchResults.failure(
                                    item,
                                    tools[index],
                                    if (error is ToolBatchSyntaxFailure && started.get(index) == 0) {
                                        ToolBatchStatus.SKIPPED
                                    } else {
                                        ToolBatchStatus.CANCELLED
                                    },
                                    if (error is java.util.concurrent.TimeoutException) ToolErrorCode.TIMEOUT else ToolErrorCode.CANCELLED,
                                    when (error) {
                                        is ToolBatchSyntaxFailure -> "Batch stopped because a child reported invalid arguments"
                                        is CancellationException -> "Batch cancelled"
                                        else -> "Batch deadline expired"
                                    },
                                )
                            },
                        )
                    } else {
                        Mono.error(error)
                    }
                }
        }

    /** Validates authorization, runtime arguments, recursion and aggregate bounds as one operation. */
    fun validate(request: ToolBatchRequest): List<VisualAgentTool> {
        val limits = request.limits
        checkBatch(
            limits.maxItems in 1..32 &&
                limits.maxConcurrency in 1..16 &&
                limits.maxArgumentCharacters in 1..262144 &&
                limits.maxResultCharacters in 1024..65536 &&
                limits.timeoutMillis in 1..600000,
            "LIMIT_EXCEEDED",
            "Invalid batch limits",
        )
        checkBatch(request.items.isNotEmpty() && request.items.size <= limits.maxItems, "LIMIT_EXCEEDED", "Batch item limit exceeded")
        checkBatch(
            request.items
                .map { it.id }
                .distinct()
                .size == request.items.size,
            "TOOL_ARGUMENTS",
            "Duplicate batch item IDs",
        )
        val available = registry.resolve(request.enabledTools.mapTo(linkedSetOf(), ::ToolId)).associateBy { it.definition.id.value }
        var argumentCharacters = 0L
        val resolved =
            request.items.map { item ->
                checkBatch(
                    item.id.isNotBlank() && item.id.length <= 128 && item.tool.isNotBlank(),
                    "TOOL_ARGUMENTS",
                    "Invalid batch identity",
                )
                val tool =
                    available[item.tool] ?: available.values.firstOrNull { it.definition.name == item.tool }
                        ?: throw ToolBatchValidationException("TOOL_ACCESS", "Batch tool is not enabled")
                checkBatch(
                    tool.definition.id.value !in setOf("javascript:execute", "tools:batch", "tool:help"),
                    "TOOL_ACCESS",
                    "Recursive tool composition is disabled",
                )
                checkBatch(
                    (item.arguments["async"] as? JsonPrimitive)?.content != "true",
                    "TOOL_ARGUMENTS",
                    "Batch children must be awaited",
                )
                // Validate timeout before any earlier sibling can mutate state.
                runCatching { runtimeOptions(item.arguments, DEFAULT_TOOL_TIMEOUT_SECONDS) }.getOrElse {
                    throw ToolBatchValidationException("TOOL_ARGUMENTS", "Invalid tool runtime arguments")
                }
                argumentCharacters += item.arguments.toString().length
                checkBatch(argumentCharacters <= limits.maxArgumentCharacters, "LIMIT_EXCEEDED", "Batch argument budget exceeded")
                tool
            }
        resolved.forEachIndexed { index, tool ->
            checkBatch(
                ToolBatchResults.capacity(request) >= ToolBatchResults.overhead(request.items[index], tool) + 78,
                "LIMIT_EXCEEDED",
                "Batch result budget too small",
            )
        }
        return resolved
    }

    private fun executeChild(
        request: ToolBatchRequest,
        tool: VisualAgentTool,
        index: Int,
        deadline: Long,
        admission: Semaphore,
        startedChildren: java.util.concurrent.atomic.AtomicIntegerArray,
    ): Mono<ToolBatchOutcome> =
        Mono.defer {
            val item = request.items[index]
            val started = System.nanoTime()
            val context =
                request.context +
                    mapOf(
                        "providerToolCallId" to
                            if (request.context["batchProviderCalls"] == true) item.id else "${request.batchId}:${item.id}",
                        "batchSequence" to index,
                        "batchId" to request.batchId,
                        "toolCallSequence" to index,
                        "toolDeadlineNanos" to deadline,
                    )
            val required = if (tool.definition.batchSafety == ToolBatchSafety.READ_ONLY_PARALLEL) 1 else request.limits.maxConcurrency
            // Semaphore admission is an explicit blocking integration on Reactor's shared scheduler.
            Mono
                .using(
                    {
                        admission.acquire(required)
                        Unit
                    },
                    {
                        Mono.using({
                            permits.acquire()
                            Unit
                        }, {
                            registry
                                .executeReactive(tool, item.arguments.toString(), context)
                                .doOnSubscribe { startedChildren.set(index, 1) }
                        }, { permits.release() })
                    },
                    { admission.release(required) },
                ).subscribeOn(Schedulers.boundedElastic())
                .map { serialized ->
                    val result = Json.decodeFromString<ToolResultEnvelope>(serialized)
                    ToolBatchResults.collect(request, item, tool, result, (System.nanoTime() - started) / 1000000)
                }.onErrorResume { error ->
                    if (error is CancellationException) {
                        Mono.error(error)
                    } else {
                        Mono.just(
                            ToolBatchResults.failure(
                                item,
                                tool,
                                ToolBatchStatus.FAILURE,
                                ToolErrorCode.EXECUTION_FAILED,
                                "Tool execution failed",
                            ),
                        )
                    }
                }
        }

    private fun checkBatch(
        condition: Boolean,
        category: String,
        message: String,
    ) {
        if (!condition) throw ToolBatchValidationException(category, message)
    }
}
