package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolExecutionScope
import reactor.core.publisher.Mono

/** Returns background results before review, with a bounded number of continuations. */
internal fun finishTodoToolWork(
    tools: ToolExecutionScope,
    token: CancellationToken?,
    respond: (String?) -> Mono<ChatResponse>,
): Mono<ChatResponse> =
    Mono.defer {
        val cancellation = token?.onCancelled(tools::close)

        /** Composes the next bounded worker continuation after tool completion. */
        fun continueWork(
            followUp: String?,
            delivered: Int,
            attempt: Int,
        ): Mono<ChatResponse> =
            Mono.defer {
                token?.throwIfCancelled()
                if (attempt >=
                    8
                ) {
                    return@defer Mono.error(IllegalStateException("Background tool continuation limit reached; task was not completed."))
                }
                respond(followUp).flatMap { response ->
                    check(response.done) { "Worker returned no terminal response" }
                    tools.awaitCompletion().then(
                        Mono.defer {
                            token?.throwIfCancelled()
                            val completed = tools.asynchronousCount()
                            if (completed == delivered) {
                                Mono.just(response)
                            } else {
                                continueWork(
                                    "The background tools for this attempt have finished. Evaluate their actual results, " +
                                        "complete any remaining work, and provide the final task result. " +
                                        "Do not repeat successful side effects.\n\n" +
                                        tools.evidence(),
                                    completed,
                                    attempt + 1,
                                )
                            }
                        },
                    )
                }
            }
        continueWork(null, 0, 0).doFinally {
            cancellation?.close()
            tools.close()
        }
    }
