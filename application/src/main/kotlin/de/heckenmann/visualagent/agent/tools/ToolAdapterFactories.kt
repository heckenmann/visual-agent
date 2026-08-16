@file:Suppress("FunctionName")

package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.api.ToolSettings
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsPort
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsUpdate
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.knowledge.MemoryStore
import de.heckenmann.visualagent.knowledge.TodoStore
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService
import org.springframework.beans.factory.ObjectProvider

/** Compatibility factory routing application canvas collaborators through the tool-owned port. */
fun CanvasTool(
    canvas: CanvasOperations,
    conversations: ConversationStore,
) = de.heckenmann.visualagent.agent.tools.canvas
    .CanvasTool(CanvasToolPortAdapter(canvas, conversations))

/** Compatibility factory routing application workspace collaborators through the tool-owned port. */
fun WorkspaceFileTool(
    files: WorkspaceFileService,
    provider: ObjectProvider<LLMProvider>,
) = de.heckenmann.visualagent.agent.tools
    .WorkspaceFileTool(WorkspaceFileToolPortAdapter(files, provider))

/** Compatibility factory routing application layout collaborators through the tool-owned port. */
fun WorkspaceLayoutTool(layout: WorkspaceLayoutService) =
    de.heckenmann.visualagent.agent.tools
        .WorkspaceLayoutTool(WorkspaceLayoutToolPortAdapter(layout))

/** Compatibility factory routing application todo collaborators through the tool-owned port. */
fun TodosTool(
    todoStore: TodoStore,
    memoryStore: MemoryStore,
    manager: AgentManager,
) = de.heckenmann.visualagent.agent.tools
    .TodosTool(TodoToolPortAdapter(todoStore, memoryStore, { manager.todoManager }, { manager }))

/** Compatibility factory routing application agent collaborators through the tool-owned port. */
fun AgentListTool(
    manager: AgentManager,
    config: AgentToolConfigService,
) = AgentListTool(AgentToolPortAdapter(manager, config, manager.memoryStore))

/** Compatibility factory routing application agent collaborators through the tool-owned port. */
fun AgentCreateTool(manager: AgentManager) =
    AgentCreateTool(AgentToolPortAdapter(manager, manager.agentToolConfigService, manager.memoryStore))

/** Compatibility factory routing application agent collaborators through the tool-owned port. */
fun AgentUpdateTool(manager: AgentManager) =
    AgentUpdateTool(AgentToolPortAdapter(manager, manager.agentToolConfigService, manager.memoryStore))

/** Compatibility factory routing application agent collaborators through the tool-owned port. */
fun AgentDeleteTool(manager: AgentManager) =
    AgentDeleteTool(AgentToolPortAdapter(manager, manager.agentToolConfigService, manager.memoryStore))

/** Compatibility factory routing application agent collaborators through the tool-owned port. */
fun AgentLogTool(manager: AgentManager) = AgentLogTool(AgentToolPortAdapter(manager, manager.agentToolConfigService, manager.memoryStore))

/** Compatibility factory routing application agent collaborators through the tool-owned port. */
fun AgentShowTool(
    manager: AgentManager,
    config: AgentToolConfigService,
) = AgentShowTool(AgentToolPortAdapter(manager, config, manager.memoryStore))

/** Factory for isolated context-tool tests with the persisted provider catalog. */
fun ContextTool(
    appConfig: AppConfigBean,
    providerCatalog: ProviderCatalogService,
) = ContextTool(LegacySettingsPort(appConfig, providerCatalog))

/** Compatibility factory supplying the mutable application timeout to the provider-neutral registry. */
fun ToolRegistry(
    tools: List<VisualAgentTool>,
    eventBus: ToolEventBus,
    appConfig: AppConfigBean,
) = de.heckenmann.visualagent.agent.tools
    .ToolRegistry(tools, eventBus) { appConfig.timeoutSeconds }

private class LegacySettingsPort(
    private val config: AppConfigBean,
    private val providerCatalog: ProviderCatalogService,
) : ToolSettingsPort {
    override fun read() =
        ToolSettings(
            config.fontSize,
            config.llmProvider,
            providerCatalog.activeModelId(),
            config.openAiBaseUrl,
            config.openAiApiKey.isNotBlank(),
            config.streamingEnabled,
            config.thinkingEnabled,
            config.timeoutSeconds,
        )

    override fun update(update: ToolSettingsUpdate): ToolSettings = read()
}
