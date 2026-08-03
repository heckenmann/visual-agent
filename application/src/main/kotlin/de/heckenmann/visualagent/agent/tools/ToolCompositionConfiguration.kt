package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.tools.api.AgentToolPort
import de.heckenmann.visualagent.agent.tools.api.CanvasToolPort
import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsPort
import de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort
import de.heckenmann.visualagent.agent.tools.api.WorkspaceLayoutToolPort
import de.heckenmann.visualagent.agent.tools.canvas.CanvasTool
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.TodoManager
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
    /** Creates the UI settings tool. */
    @Bean
    fun uiTool(settings: ToolSettingsPort) = UiTool(settings)

    /** Creates the request-context tool. */
    @Bean
    fun contextTool(settings: ToolSettingsPort) = ContextTool(settings)

    /** Creates the workspace-root tool. */
    @Bean
    fun pwdTool() = PwdTool()

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

    /** Creates the todo tool. */
    @Bean
    fun todosTool(todos: TodoToolPort) = TodosTool(todos)

    /** Creates the sub-agent list tool. */
    @Bean
    fun agentListTool(agents: AgentToolPort) = AgentListTool(agents)

    /** Creates the sub-agent creation tool. */
    @Bean
    fun agentCreateTool(agents: AgentToolPort) = AgentCreateTool(agents)

    /** Creates the sub-agent update tool. */
    @Bean
    fun agentUpdateTool(agents: AgentToolPort) = AgentUpdateTool(agents)

    /** Creates the sub-agent deletion tool. */
    @Bean
    fun agentDeleteTool(agents: AgentToolPort) = AgentDeleteTool(agents)

    /** Creates the sub-agent log tool. */
    @Bean
    fun agentLogTool(agents: AgentToolPort) = AgentLogTool(agents)

    /** Creates the sub-agent details tool. */
    @Bean
    fun agentShowTool(agents: AgentToolPort) = AgentShowTool(agents)

    /** Creates the managed workspace-file tool. */
    @Bean
    fun workspaceFileTool(files: WorkspaceFileToolPort) = WorkspaceFileTool(files)

    /** Creates the workspace-layout tool. */
    @Bean
    fun workspaceLayoutTool(layout: WorkspaceLayoutToolPort) = WorkspaceLayoutTool(layout)

    /** Creates the structured canvas tool. */
    @Bean
    fun canvasTool(canvas: CanvasToolPort) = CanvasTool(canvas)

    /** Creates the provider-neutral registry over every composed tool. */
    @Bean
    fun toolRegistry(
        tools: List<VisualAgentTool>,
        toolEventBus: ToolEventBus,
        settings: ToolSettingsPort,
    ) = ToolRegistry(tools, toolEventBus) { settings.read().timeoutSeconds }

    /** Exposes the Spring AI/provider callback adapter. */
    @Bean
    fun providerToolCallbacks(registry: ToolRegistry): ProviderToolCallbacks = SpringAiToolCallbacksAdapter(registry)
}

/** Spring AI adaptation kept at the application composition boundary. */
class SpringAiToolCallbacksAdapter(
    private val registry: ToolRegistry,
) : ProviderToolCallbacks {
    override fun functionCallbacks(
        enabledTools: Set<ProviderToolId>,
        context: Map<String, Any>,
    ): List<ToolCallback> =
        registry.resolve(enabledTools.mapTo(mutableSetOf()) { ToolId(it.value) }).map { tool ->
            /** Provider callback delegating one resolved tool to the provider-neutral registry. */
            object : ToolCallback {
                override fun getToolDefinition(): SpringToolDefinition =
                    SpringToolDefinition
                        .builder()
                        .name(tool.definition.name)
                        .description(tool.definition.description)
                        .inputSchema(tool.definition.inputSchema)
                        .build()

                override fun call(functionInput: String): String = registry.execute(tool, functionInput, context)

                override fun call(
                    functionInput: String,
                    toolContext: ToolContext?,
                ): String = registry.execute(tool, functionInput, context + (toolContext?.context ?: emptyMap()))
            }
        }
}
