@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.codex.CodexCliAccountService
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import org.springframework.stereotype.Component

/** Spring-backed lifecycle state for the desktop application. */
@Component
class ApplicationLifecycle {
    /** True after shutdown begins, when new user-facing work must not start. */
    @Volatile
    var closing: Boolean = false

    /** Marks the application as shutting down. */
    fun beginShutdown() {
        closing = true
    }
}

/** Restores persisted workspace visibility, order, and preferred widths. */
fun restoreWorkspaceWindows(
    defaults: List<ComposeWorkspaceWindow>,
    persisted: List<WorkspaceWindowState>,
): List<ComposeWorkspaceWindow> {
    if (persisted.isEmpty()) return defaults
    val persistedById = persisted.associateBy { it.id }
    return defaults
        .mapIndexed { defaultIndex, window ->
            val persistedState = persistedById[window.id]
            val restoredPreferredWidth =
                persistedState
                    ?.preferredWidth
                    ?.takeIf { it > 0 }
                    ?.toInt()
                    ?.coerceAtLeast(ComposeWorkspaceWindowBounds.MIN_WIDTH)
                    ?: window.preferredWidth
            window.copy(
                visible = persistedState?.visible ?: window.visible,
                preferredWidth = restoredPreferredWidth,
            ) to PanelSortKey(persistedState?.order ?: (Int.MAX_VALUE - defaults.size + defaultIndex), defaultIndex)
        }.sortedWith(compareBy({ it.second.persistedOrder }, { it.second.defaultOrder }))
        .map { it.first }
}

private data class PanelSortKey(
    val persistedOrder: Int,
    val defaultOrder: Int,
)

/** Bundles concrete application services used by the current Compose panel implementations. */
data class ComposePanelServices(
    val config: AppConfigBean,
    val agentManager: AgentManager,
    val llmProvider: LLMProvider,
    val providerCatalogService: ProviderCatalogService,
    val codexCliAccountService: CodexCliAccountService? = null,
    val agentToolConfigService: AgentToolConfigService,
    val toolRegistry: ToolRegistry,
    val toolEventBus: ToolEventBus,
    val todoEventBus: TodoEventBus,
    val workspaceFileService: WorkspaceFileService,
    val canvasOperations: CanvasOperations,
    val modalRequester: ComposeModalRequester,
    val onSettingsChanged: () -> Unit,
    val inFlight: InFlightStateHolder,
    val lifecycle: ApplicationLifecycle,
)
