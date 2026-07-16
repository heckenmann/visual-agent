package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatusCallbackAdapter
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component

/**
 * Single entry point for all Spring beans required by the Compose application shell.
 *
 * Replaces scattered [org.springframework.context.ConfigurableApplicationContext.getBean] calls
 * with a single `@Autowired` component. The single `springContext.getBean(ComposeApplicationDependencies::class.java)`
 * call in [runVisualAgentComposeApplication] is the last remaining manual bean lookup.
 */
@Component
class ComposeApplicationDependencies
    @Autowired
    constructor(
        val workspaceLayoutService: WorkspaceLayoutService,
        val lifecycle: ApplicationLifecycle,
        val appConfig: AppConfigBean,
        val agentManager: AgentManager,
        val llmProvider: LLMProvider,
        val providerCatalogService: ProviderCatalogService,
        val agentToolConfigService: AgentToolConfigService,
        val toolRegistry: ToolRegistry,
        val toolEventBus: ToolEventBus,
        val todoEventBus: TodoEventBus,
        val workspaceFileService: WorkspaceFileService,
        val canvasOperations: CanvasOperations,
        val agentStatusCallbackAdapter: AgentStatusCallbackAdapter,
        private val springContext: ConfigurableApplicationContext,
    ) {
        /**
         * Number of beans registered in the Spring application context.
         */
        val beanDefinitionCount: Int get() = springContext.beanDefinitionCount

        /**
         * Closes the Spring application context.
         */
        fun close() {
            springContext.close()
        }
    }
