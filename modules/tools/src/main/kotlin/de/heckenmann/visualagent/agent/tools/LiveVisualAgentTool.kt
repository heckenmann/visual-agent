package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.model.ToolLiveState
import reactor.core.publisher.Mono

/** Optional runtime-discovered capability for tools with meaningful intermediate state. */
interface LiveVisualAgentTool : VisualAgentTool {
    /** Executes once and reports semantic snapshots through the invocation-owned reporter. */
    fun executeLiveReactive(
        inputJson: String,
        context: Map<String, Any>,
        reporter: ToolLiveReporter,
    ): Mono<ToolResult>
}

/** Invocation-scoped reporting boundary; identity and ordering are assigned by the runtime. */
fun interface ToolLiveReporter {
    /** Reports an immutable snapshot; supplied identity, revision and terminal flags are ignored. */
    fun report(state: ToolLiveState)
}
