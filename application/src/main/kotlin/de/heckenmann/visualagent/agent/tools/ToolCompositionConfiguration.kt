package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.javascript.GraalJavaScriptExecutionService
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsPort
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.workspace.WorkspaceJavaScriptWriter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Composes tool implementations with application adapters and the provider boundary. */
@Configuration
class ToolCompositionConfiguration {
    /** Supplies the server-local clock used by deterministic model-facing time queries. */
    @Bean
    fun serverClock(): Clock = Clock.systemDefaultZone()

    /** Supplies system and explicit-server DNS behavior to the independent network tools. */
    @Bean
    fun hostResolver(): HostResolver = JvmHostResolver()

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

    /** Shared resource-limited executor used by models, the batch tool and JavaScript. */
    @Bean
    fun toolBatchExecutor(
        registry: ToolRegistry,
        @org.springframework.beans.factory.annotation.Value("\${visual-agent.tools.batch.global-concurrency:16}") concurrency: Int,
    ): ToolBatchExecutor = ToolBatchExecutor(registry, concurrency)

    /** Creates the lazy batch entry point without introducing a registry cycle. */
    @Bean
    fun toolBatchTool(executor: ObjectProvider<ToolBatchExecutor>): ToolBatchTool = ToolBatchTool(executor::getObject)

    /** Creates the lazy tool-help dispatcher without creating a registry dependency cycle. */
    @Bean
    fun toolHelpTool(registry: ObjectProvider<ToolRegistry>): ToolHelpTool = ToolHelpTool(registry::getObject)

    /** Creates the sandbox runtime lazily so the JavaScript tool can be part of the registry. */
    @Bean
    fun javaScriptExecutionService(
        registry: ObjectProvider<ToolRegistry>,
        workspaceWriter: WorkspaceJavaScriptWriter,
        batches: ObjectProvider<ToolBatchExecutor>,
    ): GraalJavaScriptExecutionService = GraalJavaScriptExecutionService({ registry.getObject() }, workspaceWriter, batches::getObject)

    /** Exposes the Spring AI/provider callback adapter. */
    @Bean
    fun providerToolCallbacks(
        registry: ToolRegistry,
        agentManager: ObjectProvider<AgentManager>,
        batches: ToolBatchExecutor,
    ): ProviderToolCallbacks = SpringAiToolCallbacksAdapter(registry, { agentManager.getObject() }, batches)
}
