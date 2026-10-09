package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.tools.ToolBatchExecutor
import de.heckenmann.visualagent.agent.tools.ToolBatchItem
import de.heckenmann.visualagent.agent.tools.ToolBatchLimits
import de.heckenmann.visualagent.agent.tools.ToolBatchOutcome
import de.heckenmann.visualagent.agent.tools.ToolBatchRequest
import de.heckenmann.visualagent.agent.tools.ToolBatchValidationException
import de.heckenmann.visualagent.agent.tools.ToolCancellationRegistrar
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyExecutable
import reactor.core.Disposable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Queues host completion; Graal Values and guest callbacks are touched only by the context owner. */
internal class JavaScriptBatchBridge(
    private val registry: ToolRegistry,
    private val executor: ToolBatchExecutor,
    private val enabled: Set<String>,
    private val context: Map<String, Any>,
    private val token: CancellationToken,
    private val limits: JavaScriptExecutionLimits,
    private val calls: AtomicInteger,
    private val lastFailure: AtomicReference<JavaScriptExecutionException?>,
    private val admission: java.util.concurrent.Semaphore,
) : AutoCloseable {
    private var promiseFactory: Value? = null
    private val completions = ConcurrentLinkedQueue<() -> Unit>()
    private val subscriptions = mutableListOf<Disposable>()
    private val converter =
        JavaScriptGuestValueConverter(limits.copy(maxToolArgumentCharacters = minOf(limits.maxToolArgumentCharacters, 262144)))
    private var resultCharacters = 0L
    private var argumentCharacters = 0L

    /** Creates a guest-owned Promise constructor, retained privately by the bridge. */
    fun install(context: Context) {
        promiseFactory = context.eval("js", "(executor) => new Promise(executor)")
    }

    /** Fully validates a guest batch and immediately returns a genuine pending Promise. */
    fun callMany(arguments: Array<out Value>): Any {
        try {
            token.throwIfCancelled()
            val array = arguments.singleOrNull() ?: invalid("TOOL_ARGUMENTS", "callMany requires one array")
            if (!array.hasArrayElements()) invalid("TOOL_ARGUMENTS", "callMany requires an array")
            if (array.arraySize !in 1..limits.maxBatchItems.toLong()) invalid("LIMIT_EXCEEDED", "Batch item limit exceeded")
            val available =
                registry
                    .resolve(
                        enabled
                            .map {
                                de.heckenmann.visualagent.agent.tools.api
                                    .ToolId(it)
                            }.toSet(),
                    ).associateBy { it.definition.name }
            val items =
                (0 until array.arraySize.toInt()).map { index ->
                    val entry = array.getArrayElement(index.toLong())
                    if (!entry.hasMembers() ||
                        !entry.hasMember("id") ||
                        !entry.hasMember("name") ||
                        !entry.hasMember("arguments")
                    ) {
                        invalid("TOOL_ARGUMENTS", "Invalid batch item")
                    }
                    val id =
                        entry.getMember("id").takeIf { it.isString }?.asString() ?: invalid("TOOL_ARGUMENTS", "Batch id must be a string")
                    val name =
                        entry.getMember("name").takeIf { it.isString }?.asString()
                            ?: invalid("TOOL_ARGUMENTS", "Batch name must be a string")
                    val tool = available[name] ?: invalid("TOOL_ACCESS", "Batch tool is not enabled")
                    val input = entry.getMember("arguments")
                    if (input.hasArrayElements() ||
                        input.isNull ||
                        input.canExecute()
                    ) {
                        invalid("TOOL_ARGUMENTS", "Batch arguments must be an object")
                    }
                    ToolBatchItem(id, tool.definition.id.value, converter.toJsonObject(input))
                }
            val totalArguments =
                items.sumOf {
                    it.arguments
                        .toString()
                        .length
                        .toLong()
                }
            if (argumentCharacters + totalArguments >
                minOf(limits.maxToolArgumentCharacters, 262144)
            ) {
                invalid("LIMIT_EXCEEDED", "Batch argument budget exceeded")
            }
            val remainingResults = minOf(limits.maxResultCharacters.toLong() - resultCharacters, 65536).toInt()
            if (remainingResults < 1024) invalid("LIMIT_EXCEEDED", "Batch result budget exhausted")
            val request =
                ToolBatchRequest(
                    items,
                    enabled,
                    context +
                        mapOf(
                            "javascript" to true,
                            "batchAdmission" to admission,
                            "toolCancellationRegistrar" to ToolCancellationRegistrar(token::onCancelled),
                        ),
                    ToolBatchLimits(
                        limits.maxBatchItems,
                        limits.maxConcurrentToolCalls,
                        minOf(limits.maxToolArgumentCharacters, 262144),
                        remainingResults,
                        limits.timeoutMillis.coerceAtMost(600000),
                    ),
                )
            executor.validate(request)
            if (calls.get() + items.size > limits.maxToolCalls) invalid("LIMIT_EXCEEDED", "JavaScript tool-call limit exceeded")
            calls.addAndGet(items.size)
            argumentCharacters += totalArguments
            // Reserve output capacity before concurrent Promise requests can overcommit it.
            resultCharacters += remainingResults
            return promiseFactory!!.execute(
                ProxyExecutable { callbacks ->
                    val resolve = callbacks[0]
                    val reject = callbacks[1]
                    subscriptions +=
                        executor.execute(request).subscribe(
                            { results ->
                                completions.add {
                                    val expired = (request.context["toolDeadlineNanos"] as? Long)?.let { System.nanoTime() >= it } == true
                                    val invalid =
                                        results.firstOrNull {
                                            it.errorCode ==
                                                de.heckenmann.visualagent.agent.tools.api.ToolErrorCode.INVALID_ARGUMENT
                                        }
                                    if (invalid != null) {
                                        val failure =
                                            JavaScriptExecutionException(
                                                JavaScriptErrorCategory.TOOL_ARGUMENTS,
                                                "Tool batch stopped at invalid arguments: ${invalid.error}".take(500),
                                            )
                                        lastFailure.set(failure)
                                        reject.execute(failure.message)
                                    } else if (token.isCancelled || expired) {
                                        val category =
                                            if (token.isCancelled) {
                                                JavaScriptErrorCategory.CANCELLED
                                            } else {
                                                JavaScriptErrorCategory.TIMEOUT
                                            }
                                        val failure = JavaScriptExecutionException(category, "Tool batch did not complete")
                                        lastFailure.set(failure)
                                        reject.execute(failure.message)
                                    } else {
                                        val actual =
                                            kotlinx.serialization.json.Json
                                                .encodeToString(
                                                    kotlinx.serialization.builtins.ListSerializer(ToolBatchOutcome.serializer()),
                                                    results,
                                                ).length
                                        resultCharacters -= (remainingResults - actual).coerceAtLeast(0)
                                        resolve.execute(outcomes(results))
                                    }
                                }
                            },
                            { error ->
                                completions.add {
                                    val category =
                                        if (token.isCancelled) {
                                            JavaScriptErrorCategory.CANCELLED
                                        } else if (error is java.util.concurrent.TimeoutException) {
                                            JavaScriptErrorCategory.TIMEOUT
                                        } else {
                                            JavaScriptErrorCategory.TOOL_FAILURE
                                        }
                                    val failure = JavaScriptExecutionException(category, "Tool batch did not complete")
                                    lastFailure.set(failure)
                                    reject.execute(failure.message)
                                }
                            },
                        )
                    null
                },
            )
        } catch (error: ToolBatchValidationException) {
            val failure =
                JavaScriptExecutionException(
                    JavaScriptErrorCategory.valueOf(error.category),
                    error.message ?: "Invalid tool batch",
                )
            lastFailure.set(failure)
            throw failure
        } catch (error: JavaScriptExecutionException) {
            lastFailure.set(error)
            throw error
        }
    }

    /** Pumps queued completion on the same thread that owns the Graal context. */
    fun drain() {
        while (true) (completions.poll() ?: return).invoke()
    }

    override fun close() {
        subscriptions.forEach(Disposable::dispose)
        subscriptions.clear()
        completions.clear()
    }

    private fun outcomes(results: List<ToolBatchOutcome>): ProxyArray =
        ProxyArray.fromList(
            results.map { result ->
                JavaScriptToolResults.envelope(result.result, result.id)
            },
        )

    private fun invalid(
        category: String,
        message: String,
    ): Nothing = throw ToolBatchValidationException(category, message)
}
