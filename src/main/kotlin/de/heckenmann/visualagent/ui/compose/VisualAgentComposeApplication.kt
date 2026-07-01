@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.VisualAgentApplication
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import de.heckenmann.visualagent.workspace.layout.DesktopState
import de.heckenmann.visualagent.workspace.layout.StageState
import de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService
import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import kotlinx.coroutines.launch
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import kotlin.math.roundToInt

/**
 * Runs the Compose Multiplatform Visual Agent desktop application.
 */
fun runVisualAgentComposeApplication() {
    AppIdentity.configureProcessProperties()
    val springContext =
        SpringApplicationBuilder(VisualAgentApplication::class.java)
            .web(WebApplicationType.NONE)
            .run()

    application {
        Window(
            onCloseRequest = {
                springContext.close()
                exitApplication()
            },
            title = AppIdentity.DISPLAY_NAME,
            icon = painterResource("icons/visual-agent.png"),
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            VisualAgentComposeApp(
                config = AppConfig.instance,
                springContext = springContext,
                workspaceLayoutService = springContext.getBean(WorkspaceLayoutService::class.java),
                onCloseApplication = {
                    springContext.close()
                    exitApplication()
                },
            )
        }
    }
}

@Composable
private fun VisualAgentComposeApp(
    config: AppConfig,
    springContext: ConfigurableApplicationContext,
    workspaceLayoutService: WorkspaceLayoutService,
    onCloseApplication: () -> Unit,
) {
    var windows by remember { mutableStateOf(restoreWorkspaceWindows(defaultWindows(), workspaceLayoutService.report().windows)) }
    var modal by remember { mutableStateOf<ComposeModal?>(null) }
    var commandPaletteVisible by remember { mutableStateOf(false) }
    var uiFontSize by remember { mutableStateOf(config.fontSize) }
    var settingsRevision by remember { mutableStateOf(0) }
    val workspaceFocusRequester = remember { FocusRequester() }
    val composeScope = rememberCoroutineScope()
    val panelServices =
        remember {
            ComposePanelServices(
                config = config,
                agentManager = springContext.getBean(AgentManager::class.java),
                llmProvider = springContext.getBean(LLMProvider::class.java),
                providerCatalogService = springContext.getBean(ProviderCatalogService::class.java),
                agentToolConfigService = springContext.getBean(AgentToolConfigService::class.java),
                toolRegistry = springContext.getBean(ToolRegistry::class.java),
                workspaceFileService = springContext.getBean(WorkspaceFileService::class.java),
                canvasOperations = springContext.getBean(CanvasOperations::class.java),
                modalRequester = ComposeModalRequester { requested -> modal = requested },
                onSettingsChanged = {
                    uiFontSize = config.fontSize
                    settingsRevision += 1
                },
            )
        }
    val toggleWindow: (String) -> Unit = { id ->
        windows = toggleWorkspacePanel(windows, id)
    }
    val activateWindow: (String) -> Unit = { id ->
        windows = windows.map { window -> if (window.id == id) window.copy(visible = true) else window }
    }
    val moveWindowEarlier: (String) -> Unit = { id ->
        windows = moveWorkspacePanel(windows, id, ComposePanelMoveDirection.Earlier)
    }
    val moveWindowLater: (String) -> Unit = { id ->
        windows = moveWorkspacePanel(windows, id, ComposePanelMoveDirection.Later)
    }
    val commands =
        windows.map { window ->
            ComposeCommand(
                id = "open-${window.id}",
                title = "Open ${window.title}",
                description = window.subtitle,
            ) {
                activateWindow(window.id)
            }
        } +
            ComposeCommand(
                id = "close-application",
                title = "Close application",
                description = "Close Visual Agent and persist workspace state",
                action = onCloseApplication,
            )
    LaunchedEffect(Unit) {
        workspaceFocusRequester.requestFocus()
    }
    DisposableEffect(workspaceLayoutService) {
        val handle =
            workspaceLayoutService.addWindowStateListener { states ->
                composeScope.launch {
                    windows = restoreWorkspaceWindows(windows, states)
                }
            }
        onDispose { handle.close() }
    }

    MaterialTheme(colorScheme = draculaColorScheme(), typography = visualAgentTypography(uiFontSize)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(workspaceFocusRequester)
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.isCommandPaletteShortcut() -> {
                                        commandPaletteVisible = true
                                        true
                                    }
                                    event.workspaceShortcutDigit() != null -> {
                                        panelIdForShortcutDigit(event.workspaceShortcutDigit()!!)?.let(toggleWindow)
                                        true
                                    }
                                    else -> false
                                }
                            }.focusable()
                            .background(backgroundBrush()),
                ) {
                    ComposeRail(
                        windows = windows,
                        onToggleWindow = toggleWindow,
                        onMoveWindowEarlier = moveWindowEarlier,
                        onMoveWindowLater = moveWindowLater,
                        onCloseApplication = onCloseApplication,
                    )
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                    ) {
                        val viewport =
                            ComposeWorkspaceViewport(
                                width = maxWidth.value.roundToInt(),
                                height = maxHeight.value.roundToInt(),
                            )
                        val splitBounds = splitWorkspaceBounds(windows, viewport)
                        val workspaceStates =
                            windows.mapIndexed {
                                index,
                                window,
                                ->
                                window.toWorkspaceWindowState(viewport, splitBounds[window.id], index)
                            }
                        workspaceLayoutService.bind(
                            stage = StageState(width = viewport.width.toDouble(), height = viewport.height.toDouble()),
                            desktop = DesktopState(width = viewport.width.toDouble(), height = viewport.height.toDouble()),
                            windows = workspaceStates,
                        )
                        LaunchedEffect(workspaceStates) {
                            workspaceLayoutService.applyWindowStates(workspaceStates, notifyListeners = false)
                        }
                        val activeProvider =
                            remember(settingsRevision) {
                                panelServices.providerCatalogService.getProvider(panelServices.providerCatalogService.activeProviderId())
                            }
                        Column(modifier = Modifier.fillMaxSize()) {
                            ComposeWorkspaceHeader(
                                providerName = activeProvider?.id ?: config.llmProvider,
                                modelName = activeProvider?.defaultModel.orEmpty().ifBlank { config.activeModel() },
                                beanDefinitionCount = springContext.beanDefinitionCount,
                            )
                            ComposeSplitWorkspace(
                                windows = windows,
                                panelServices = panelServices,
                                onToggleWindow = toggleWindow,
                                onMoveWindowEarlier = moveWindowEarlier,
                                onMoveWindowLater = moveWindowLater,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                            )
                        }
                    }
                }
                ComposeModalHost(modal = modal, onDismiss = { modal = null })
                ComposeCommandPaletteHost(
                    visible = commandPaletteVisible,
                    commands = commands,
                    onDismiss = { commandPaletteVisible = false },
                )
            }
        }
    }
}

private fun defaultWindows(): List<ComposeWorkspaceWindow> =
    workspacePanelShortcutIds.map { id ->
        when (id) {
            "chat" ->
                ComposeWorkspaceWindow(
                    id = "chat",
                    icon = "C",
                    title = "Conversation",
                    subtitle = "Main agent conversation and history",
                    bounds = ComposeWorkspaceWindowBounds(x = 24, y = 92, width = 520, height = 460),
                )
            "todos" ->
                ComposeWorkspaceWindow(
                    id = "todos",
                    icon = "T",
                    title = "Todos",
                    subtitle = "DB-backed task management",
                    bounds = ComposeWorkspaceWindowBounds(x = 570, y = 92, width = 420, height = 420),
                )
            "files" ->
                ComposeWorkspaceWindow(
                    id = "files",
                    icon = "F",
                    title = "Files",
                    subtitle = "Workspace import, sync, rename, delete",
                    bounds = ComposeWorkspaceWindowBounds(x = 120, y = 570, width = 540, height = 340),
                    visible = false,
                )
            "agents" ->
                ComposeWorkspaceWindow(
                    id = "agents",
                    icon = "A",
                    title = "Subagents",
                    subtitle = "Worker creation and live job counts",
                    bounds = ComposeWorkspaceWindowBounds(x = 690, y = 540, width = 460, height = 350),
                    visible = false,
                )
            "settings" ->
                ComposeWorkspaceWindow(
                    id = "settings",
                    icon = "S",
                    title = "Settings",
                    subtitle = "Provider, model, theme, font size",
                    bounds = ComposeWorkspaceWindowBounds(x = 760, y = 140, width = 420, height = 360),
                    visible = false,
                )
            "canvas" ->
                ComposeWorkspaceWindow(
                    id = "canvas",
                    icon = "D",
                    title = "Canvas",
                    subtitle = "Structured drawing and capture",
                    bounds = ComposeWorkspaceWindowBounds(x = 260, y = 170, width = 620, height = 460),
                    visible = false,
                )
            else -> error("Unsupported workspace panel id: $id")
        }
    }

private fun ComposeWorkspaceWindow.toWorkspaceWindowState(
    viewport: ComposeWorkspaceViewport,
    splitBounds: ComposeWorkspaceWindowBounds?,
    orderIndex: Int,
): WorkspaceWindowState {
    val coercedBounds = splitBounds ?: bounds.coerceIn(viewport)
    return WorkspaceWindowState(
        id = id,
        x = coercedBounds.x.toDouble(),
        y = coercedBounds.y.toDouble(),
        width = coercedBounds.width.toDouble(),
        height = coercedBounds.height.toDouble(),
        visible = visible,
        zIndex = orderIndex,
    )
}

private fun androidx.compose.ui.input.key.KeyEvent.workspaceShortcutDigit(): Int? {
    if (type != KeyEventType.KeyDown || (!isMetaPressed && !isCtrlPressed)) return null
    return when (key) {
        Key.One -> 1
        Key.Two -> 2
        Key.Three -> 3
        Key.Four -> 4
        Key.Five -> 5
        Key.Six -> 6
        else -> null
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.isCommandPaletteShortcut(): Boolean =
    type == KeyEventType.KeyDown && (isMetaPressed || isCtrlPressed) && key == Key.K
