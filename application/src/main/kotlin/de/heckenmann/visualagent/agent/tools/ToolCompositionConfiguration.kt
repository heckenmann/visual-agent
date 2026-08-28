package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ProviderToolCall
import de.heckenmann.visualagent.agent.javascript.GraalJavaScriptExecutionService
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsPort
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.workspace.WorkspaceJavaScriptWriter
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.ToolCallback
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import de.heckenmann.visualagent.agent.ToolId as ProviderToolId
import org.springframework.ai.tool.definition.ToolDefinition as SpringToolDefinition

/** Composes tool implementations with application adapters and the provider boundary. */
@Configuration
class ToolCompositionConfiguration {
    /** Adapts todo persistence and lifecycle services without eagerly creating the agent manager. */
    @Bean
    fun todoToolPort(
        todoStore: TodoStore,
        memoryStore: MemoryStore,
        todoManager: ObjectProvider<TodoManager>,
        agentManager: ObjectProvider<AgentManager>,
    ): TodoToolPort =
        TodoToolPortAdapter(
            todoStore,
            memoryStore,
            todoManager::getObject,
            agentManager::getObject,
        )

    /** Creates the provider-neutral registry over every composed tool. */
    @Bean
    fun toolRegistry(
        tools: List<VisualAgentTool>,
        toolEventBus: ToolEventBus,
        settings: ToolSettingsPort,
    ) = ToolRegistry(tools, toolEventBus) { settings.read().timeoutSeconds }

    /** Creates the sandbox runtime lazily so the JavaScript tool can be part of the registry. */
    @Bean
    fun javaScriptExecutionService(
        registry: ObjectProvider<ToolRegistry>,
        workspaceWriter: WorkspaceJavaScriptWriter,
    ): GraalJavaScriptExecutionService = GraalJavaScriptExecutionService({ registry.getObject() }, workspaceWriter)

    /** Exposes the Spring AI/provider callback adapter. */
    @Bean
    fun providerToolCallbacks(registry: ToolRegistry): ProviderToolCallbacks = SpringAiToolCallbacksAdapter(registry)
}

/** Spring AI adaptation kept at the application composition boundary. */
class SpringAiToolCallbacksAdapter(
    private val registry: ToolRegistry,
) : ProviderToolCallbacks {
    private val callCorrelation = ThreadLocal<List<CorrelatedToolCall>?>()

    override fun functionCallbacks(
        enabledTools: Set<ProviderToolId>,
        context: Map<String, Any>,
    ): List<ToolCallback> {
        val requestContext =
            context +
                ("enabledTools" to enabledTools.map { it.value }.toSet()) +
                toolCancellationRegistrar(context)
        return registry.resolve(enabledTools.mapTo(mutableSetOf()) { ToolId(it.value) }).map { tool ->
            /** Provider callback delegating one resolved tool to the provider-neutral registry. */
            object : ToolCallback {
                override fun getToolDefinition(): SpringToolDefinition =
                    SpringToolDefinition
                        .builder()
                        .name(registry.definition(tool).name)
                        .description(registry.definition(tool).description)
                        .inputSchema(registry.definition(tool).inputSchema)
                        .build()

                override fun call(functionInput: String): String =
                    registry.execute(
                        tool,
                        functionInput,
                        correlatedContext(tool.definition.name, functionInput, requestContext),
                    )

                override fun call(
                    functionInput: String,
                    toolContext: ToolContext?,
                ): String =
                    registry.execute(
                        tool,
                        functionInput,
                        correlatedContext(tool.definition.name, functionInput, requestContext + (toolContext?.context ?: emptyMap())),
                    )
            }
        }
    }

    override fun toolRuntimeGuidance(): String = registry.runtimeGuidance()

    private fun toolCancellationRegistrar(context: Map<String, Any>): Map<String, Any> {
        val parent = context["cancellationToken"] as? CancellationToken ?: return emptyMap()
        return mapOf("toolCancellationRegistrar" to ToolCancellationRegistrar(parent::onCancelled))
    }

    override fun bindToolCallRound(
        toolCalls: List<ProviderToolCall>,
        round: Int,
    ): AutoCloseable {
        check(callCorrelation.get() == null) { "Tool-call correlation scope is already active" }
        callCorrelation.set(toolCalls.mapIndexed { sequence, call -> CorrelatedToolCall(call, round, sequence) })
        return AutoCloseable { callCorrelation.remove() }
    }

    private fun correlatedContext(
        functionName: String,
        input: String,
        context: Map<String, Any>,
    ): Map<String, Any> {
        val calls = callCorrelation.get() ?: return context
        val index = calls.indexOfFirst { it.call.functionName == functionName && it.call.argumentsJson == input }
        if (index < 0) return context
        val correlated = calls[index]
        callCorrelation.set(calls.toMutableList().also { it.removeAt(index) })
        return context +
            mapOf(
                "providerToolCallId" to correlated.call.id,
                "toolCallRound" to correlated.round,
                "toolCallSequence" to correlated.sequence,
            )
    }

    /** Provider call identity awaiting callback execution in one loop round. */
    private data class CorrelatedToolCall(
        val call: ProviderToolCall,
        val round: Int,
        val sequence: Int,
    )
}
