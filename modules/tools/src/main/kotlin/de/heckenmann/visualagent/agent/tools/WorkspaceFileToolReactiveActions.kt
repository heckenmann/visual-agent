package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import reactor.core.publisher.Mono

/** Keeps the native reactive workspace-file actions outside the command dispatcher. */
internal class WorkspaceFileToolReactiveActions(
    private val workspaceFiles: WorkspaceFileToolPort,
    private val mediaActions: WorkspaceFileToolMediaActions,
) {
    /** Executes the reactive image-analysis action, or returns null for synchronous actions. */
    fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): Mono<ToolResult>? {
        val input = parseObject(inputJson)
        if (input.string("action") != "analyzeImage") return null
        return Mono
            .fromCallable { workspaceFiles.requireFile(input.string("id"), input.string("path")) to input.requiredString("prompt") }
            .flatMap { (record, prompt) -> mediaActions.analyzeImageReactive(record, prompt) }
            .map { result -> success(TOOL_ID, result.toString()) }
            .onErrorResume { error -> Mono.just(failure(TOOL_ID, error.message ?: error::class.simpleName.orEmpty())) }
    }

    private companion object {
        const val TOOL_ID = "workspace:file"
    }
}
