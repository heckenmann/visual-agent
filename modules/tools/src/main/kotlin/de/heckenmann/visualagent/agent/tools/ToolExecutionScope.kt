package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolResult
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

/** Owns tool completion and bounded evidence for one worker attempt, including background calls. */
@Component
@Scope("prototype")
class ToolExecutionScope : AutoCloseable {
    private val lock = Any()
    private val calls = mutableListOf<Call>()
    private var closed = false

    /** Registers work before it can be scheduled or acknowledged to the model. */
    fun register(
        toolId: String,
        asynchronous: Boolean,
    ): Call =
        synchronized(lock) {
            check(!closed) { "Tool execution scope is closed" }
            Call(toolId, asynchronous).also { calls += it }
        }

    /** Waits for every call registered in the current provider turn. */
    fun awaitCompletion(): Mono<Void> =
        Mono.defer {
            val pending = synchronized(lock) { calls.map { it.completion.asMono() } }
            Mono.whenDelayError(pending).then(
                Mono.defer {
                    if (synchronized(lock) { calls.size } == pending.size) Mono.empty<Void>() else awaitCompletion()
                },
            )
        }

    /** Number of background calls whose real results must be returned to the worker. */
    fun asynchronousCount(): Int = synchronized(lock) { calls.count { it.asynchronous } }

    /** Bounded, invocation-ordered evidence; no other attempt shares this scope. */
    fun evidence(): String =
        synchronized(lock) {
            if (calls.isEmpty()) return@synchronized "No tool execution evidence was recorded for this attempt."
            val entries =
                calls.takeLast(32).map { call ->
                    val result = call.result
                    "Tool: ${call.toolId}; outcome: ${if (result == null) {
                        "PENDING"
                    } else if (result.success) {
                        "SUCCESS"
                    } else {
                        "FAILURE"
                    }}\n" +
                        (result?.error ?: result?.content ?: "No terminal result").take(2000)
                }
            (if (calls.size > 32) "Earlier tool evidence omitted; ${calls.size} calls total.\n" else "") + entries.joinToString("\n\n")
        }

    /** Cancels work still owned by an unsuccessful or cancelled worker attempt. */
    override fun close() {
        val snapshot =
            synchronized(lock) {
                closed = true
                calls.toList()
            }
        snapshot.forEach(Call::cancel)
    }

    /** One registered invocation and its terminal completion signal. */
    class Call internal constructor(
        val toolId: String,
        val asynchronous: Boolean,
    ) {
        internal val completion = Sinks.one<ToolResult>()

        @Volatile internal var result: ToolResult? = null
        private val lock = Any()
        private var execution: Disposable? = null
        private var cancelled = false

        /** Connects a detached subscription to its owning attempt. */
        fun attach(subscription: Disposable) =
            synchronized(lock) {
                if (cancelled) subscription.dispose() else execution = subscription
            }

        /** Publishes exactly one terminal result. */
        fun finish(value: ToolResult) =
            synchronized(lock) {
                if (result == null) {
                    result = value
                    completion.tryEmitValue(value)
                }
                Unit
            }

        internal fun cancel() =
            synchronized(lock) {
                cancelled = true
                if (result == null) {
                    execution?.dispose()
                    finish(failure(toolId, "TOOL_CANCELLED: Worker attempt ended."))
                }
            }
    }
}
