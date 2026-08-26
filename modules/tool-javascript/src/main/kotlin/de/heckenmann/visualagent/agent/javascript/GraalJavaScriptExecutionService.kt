package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyExecutable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/** Executes request-provided JavaScript in an isolated GraalJS context. */
class GraalJavaScriptExecutionService(
    private val registryProvider: () -> ToolRegistry,
    private val workspaceWriter: JavaScriptWorkspaceWriter,
) : AutoCloseable {
    private val executor = Executors.newCachedThreadPool()
    private val contextFactory = JavaScriptContextFactory()

    /** Execute one script and return only its final value plus bounded diagnostics. */
    fun execute(request: JavaScriptExecutionRequest): JavaScriptExecutionResult {
        validate(request)
        val localToken = CancellationToken()
        val parentRegistration = request.cancellationToken?.onCancelled(localToken::cancel)
        val contextReference = AtomicReference<Context>()
        val workerReference = AtomicReference<Thread>()
        val logs = mutableListOf<JavaScriptLogEntry>()
        val future =
            executor.submit<JavaScriptExecutionResult> {
                val worker = Thread.currentThread()
                workerReference.set(worker)
                try {
                    executeInContext(request, localToken, contextReference, logs)
                } finally {
                    workerReference.compareAndSet(worker, null)
                }
            }
        val cancellationRegistration =
            localToken.onCancelled {
                contextReference.get()?.close(true)
                workerReference.get()?.interrupt()
            }
        return try {
            future.get(request.limits.timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            localToken.cancel()
            future.cancel(true)
            throw JavaScriptExecutionException(JavaScriptErrorCategory.TIMEOUT, "JavaScript execution timed out")
        } catch (_: CancellationException) {
            throw JavaScriptExecutionException(JavaScriptErrorCategory.CANCELLED, "JavaScript execution was cancelled")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            localToken.cancel()
            future.cancel(true)
            throw JavaScriptExecutionException(JavaScriptErrorCategory.CANCELLED, "JavaScript execution was interrupted")
        } catch (error: Exception) {
            if (localToken.isCancelled) {
                throw JavaScriptExecutionException(JavaScriptErrorCategory.CANCELLED, "JavaScript execution was cancelled")
            }
            throw mapFailure(error)
        } finally {
            cancellationRegistration.close()
            parentRegistration?.close()
            localToken.cancel()
        }
    }

    /** Read a bounded JavaScript source file through the hardened workspace boundary. */
    fun readWorkspaceSource(
        relativePath: String,
        maxBytes: Long,
    ): String = workspaceWriter.read(relativePath, maxBytes).content

    override fun close() {
        executor.shutdownNow()
    }

    private fun executeInContext(
        request: JavaScriptExecutionRequest,
        token: CancellationToken,
        contextReference: AtomicReference<Context>,
        logs: MutableList<JavaScriptLogEntry>,
    ): JavaScriptExecutionResult {
        token.throwIfCancelled()
        val bridge =
            JavaScriptToolBridge(
                registry = registryProvider(),
                workspaceWriter = workspaceWriter,
                enabledTools = request.enabledTools,
                requestContext = request.requestContext,
                cancellationToken = token,
                limits = request.limits,
                logs = logs,
            )
        val context = contextFactory.create(bridge, request.limits)
        contextReference.set(context)
        return try {
            val source = "(async function() {\n${request.source}\n})()"
            val value = context.eval("js", source)
            val result = awaitResult(value, token, bridge)
            val converted = convertFinalValue(result, JavaScriptResultBudget(request.limits.maxResultCharacters))
            JavaScriptExecutionResult(converted, logs.toList())
        } catch (error: JavaScriptExecutionException) {
            throw error
        } catch (error: PolyglotException) {
            throw mapPolyglotFailure(error, bridge)
        } finally {
            context.close(true)
            contextReference.set(null)
        }
    }

    private fun awaitResult(
        value: Value,
        token: CancellationToken,
        bridge: JavaScriptToolBridge,
    ): Value {
        if (!value.hasMember("then")) return value
        val holder = arrayOfNulls<Value>(1)
        val failure = arrayOfNulls<Throwable>(1)
        val latch = java.util.concurrent.CountDownLatch(1)
        val resolve =
            ProxyExecutable { arguments ->
                holder[0] = arguments.firstOrNull()
                latch.countDown()
                null
            }
        val reject =
            ProxyExecutable { arguments ->
                failure[0] =
                    JavaScriptExecutionException(
                        JavaScriptErrorCategory.RUNTIME,
                        arguments.firstOrNull()?.toString() ?: "JavaScript promise rejected",
                    )
                latch.countDown()
                null
            }
        value.invokeMember("then", resolve, reject)
        while (!latch.await(25, TimeUnit.MILLISECONDS)) token.throwIfCancelled()
        failure[0]?.let { rejected ->
            val bridgeFailure = bridge.lastFailure.getAndSet(null)
            val rejectedMessage = rejected.message.orEmpty()
            if (
                bridgeFailure != null &&
                (
                    !looksLikeJavaScriptError(rejectedMessage) ||
                        rejectedMessage == "JavaScript promise rejected" ||
                        rejectedMessage == "[object Object]" ||
                        rejectedMessage.contains(bridgeFailure.category.name) ||
                        rejectedMessage.contains(bridgeFailure.message)
                )
            ) {
                throw bridgeFailure
            }
            throw rejected
        }
        return holder[0] ?: throw JavaScriptExecutionException(JavaScriptErrorCategory.RUNTIME, "JavaScript returned no result")
    }

    private fun convertFinalValue(
        value: Value,
        budget: JavaScriptResultBudget,
    ): Any? {
        if (value.isNull) return null
        if (value.isBoolean) return value.asBoolean().also { budget.consume(it.toString().length) }
        if (value.isNumber) return value.asDouble().also { budget.consume(it.toString().length) }
        if (value.isString) return value.asString().also { budget.consume(it.length) }
        if (value.hasArrayElements()) {
            val arraySize = value.arraySize
            if (arraySize > budget.remainingElements().toLong()) {
                throw JavaScriptExecutionException(JavaScriptErrorCategory.LIMIT_EXCEEDED, "JavaScript result size limit exceeded")
            }
            val result = ArrayList<Any?>(arraySize.toInt())
            repeat(arraySize.toInt()) { index ->
                budget.consume(1)
                result += convertFinalValue(value.getArrayElement(index.toLong()), budget)
            }
            return result
        }
        if (value.hasMembers()) {
            val result = LinkedHashMap<String, Any?>()
            value.memberKeys.forEach { key ->
                budget.consume(key.length + 3)
                result[key] = convertFinalValue(value.getMember(key), budget)
            }
            return result
        }
        throw JavaScriptExecutionException(JavaScriptErrorCategory.RUNTIME, "JavaScript returned an unsupported value")
    }

    private fun validate(request: JavaScriptExecutionRequest) {
        if (request.source.isBlank()) {
            throw JavaScriptExecutionException(
                JavaScriptErrorCategory.SYNTAX,
                "JavaScript source must not be blank",
            )
        }
        if (request.limits.timeoutMillis <= 0 || request.limits.maxToolCalls <= 0 || request.limits.maxConcurrentToolCalls <= 0) {
            throw JavaScriptExecutionException(JavaScriptErrorCategory.INTERNAL, "Invalid JavaScript execution limits")
        }
        if (request.limits.maxGuestHeapBytes <= 0 || request.limits.maxIsolateMemoryBytes < request.limits.maxGuestHeapBytes) {
            throw JavaScriptExecutionException(JavaScriptErrorCategory.INTERNAL, "Invalid JavaScript memory limits")
        }
        if (
            request.limits.maxResultCharacters <= 0 ||
            request.limits.maxWorkspaceReadBytes <= 0 ||
            request.limits.maxWorkspaceReadTotalBytes <= 0 ||
            request.limits.maxToolArgumentCharacters <= 0
        ) {
            throw JavaScriptExecutionException(JavaScriptErrorCategory.INTERNAL, "Invalid JavaScript execution limits")
        }
    }

    private fun mapFailure(error: Exception): JavaScriptExecutionException {
        val causes = generateSequence(error as Throwable) { it.cause }.toList()
        causes.firstOrNull { it is JavaScriptExecutionException }?.let { return it as JavaScriptExecutionException }
        causes.firstOrNull { it is PolyglotException }?.let { return mapPolyglotFailure(it as PolyglotException) }
        val message =
            causes
                .asSequence()
                .mapNotNull { it.message }
                .firstOrNull()
                .orEmpty()
        val category =
            when {
                message.contains(
                    "not enabled",
                    ignoreCase = true,
                ) ||
                    message.contains("Recursive JavaScript", ignoreCase = true) -> JavaScriptErrorCategory.TOOL_ACCESS
                message.contains("arguments", ignoreCase = true) -> JavaScriptErrorCategory.TOOL_ARGUMENTS
                message.contains("limit", ignoreCase = true) -> JavaScriptErrorCategory.LIMIT_EXCEEDED
                else -> JavaScriptErrorCategory.INTERNAL
            }
        return JavaScriptExecutionException(category, message.take(MAX_ERROR_CHARACTERS).ifBlank { "JavaScript execution failed" })
    }

    private fun mapPolyglotFailure(
        error: PolyglotException,
        bridge: JavaScriptToolBridge? = null,
    ): JavaScriptExecutionException {
        if (error.isResourceExhausted) {
            val category =
                if (isCpuTimeLimit(error)) {
                    JavaScriptErrorCategory.TIMEOUT
                } else {
                    JavaScriptErrorCategory.LIMIT_EXCEEDED
                }
            val message =
                if (category == JavaScriptErrorCategory.TIMEOUT) {
                    "JavaScript execution timed out"
                } else {
                    "JavaScript resource limit exceeded"
                }
            return JavaScriptExecutionException(category, message)
        }
        if (error.isHostException) {
            val host = runCatching { error.asHostException() }.getOrNull()
            if (host is JavaScriptExecutionException) return host
            bridge?.lastFailure?.getAndSet(null)?.let { return it }
        }
        val category = if (error.isSyntaxError) JavaScriptErrorCategory.SYNTAX else JavaScriptErrorCategory.RUNTIME
        return JavaScriptExecutionException(category, safePolyglotMessage(error))
    }

    private fun safePolyglotMessage(error: PolyglotException): String =
        error.message
            ?.lineSequence()
            ?.firstOrNull()
            ?.take(MAX_ERROR_CHARACTERS)
            .orEmpty()
            .ifBlank { "JavaScript execution failed" }

    private fun isCpuTimeLimit(error: PolyglotException): Boolean =
        error.message
            ?.contains("CPU time limit", ignoreCase = true)
            ?: false

    private fun looksLikeJavaScriptError(message: String): Boolean =
        message.matches(Regex("(?i)^(error|typeerror|referenceerror|rangeerror|syntaxerror):.*"))

    private companion object {
        const val MAX_ERROR_CHARACTERS = 500
    }
}
