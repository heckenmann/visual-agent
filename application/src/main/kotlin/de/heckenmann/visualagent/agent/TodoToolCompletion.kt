package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolExecutionScope
import kotlinx.coroutines.reactor.awaitSingleOrNull

/** Returns background results to the worker before review, with a bounded number of continuations. */
internal suspend fun finishTodoToolWork(
    tools: ToolExecutionScope,
    token: CancellationToken?,
    respond: suspend (String?) -> ChatResponse,
): ChatResponse {
    val cancellation = token?.onCancelled(tools::close)
    try {
        var followUp: String? = null
        var delivered = 0
        repeat(8) {
            token?.throwIfCancelled()
            val response = respond(followUp)
            check(response.done) { "Worker returned no terminal response" }
            tools.awaitCompletion().awaitSingleOrNull()
            token?.throwIfCancelled()
            val completed = tools.asynchronousCount()
            if (completed == delivered) return response
            delivered = completed
            followUp = "The background tools for this attempt have finished. Evaluate their actual results, " +
                "complete any remaining work, and provide the final task result. Do not repeat successful side effects.\n\n" +
                tools.evidence()
        }
        error("Background tool continuation limit reached; task was not completed.")
    } finally {
        cancellation?.close()
        tools.close()
    }
}
