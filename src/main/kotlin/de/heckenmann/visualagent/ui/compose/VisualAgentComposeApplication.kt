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
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import de.heckenmann.visualagent.agent.tools.ToolEventBus
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
    val workspaceLayoutService = springContext.getBean(WorkspaceLayoutService::class.java)
    val persistedStage = workspaceLayoutService.report().stage
    val defaultWidth = 1280.dp
    val defaultHeight = 820.dp
    val lifecycle = ApplicationLifecycle()

    application {
        val windowState =
            rememberWindowState(
                width = persistedStage?.width?.dp ?: defaultWidth,
                height = persistedStage?.height?.dp ?: defaultHeight,
            )
        val saveStageOnExit = {
            val size = windowState.size
            val stageWidth = size.width.value.toDouble()
            val stageHeight = size.height.value.toDouble()
            workspaceLayoutService.saveStage(StageState(width = stageWidth, height = stageHeight))
        }
        val closeApplication = {
            lifecycle.beginShutdown()
            saveStageOnExit()
            springContext.close()
            exitApplication()
        }
        Window(
            onCloseRequest = closeApplication,
            title = AppIdentity.DISPLAY_NAME,
            icon = painterResource("icons/visual-agent.png"),
            state = windowState,
        ) {
            VisualAgentComposeApp(
                config = AppConfig.instance,
                springContext = springContext,
                workspaceLayoutService = workspaceLayoutService,
                lifecycle = lifecycle,
                onCloseApplication = closeApplication,
            )
        }
    }
}

@Composable
private fun VisualAgentComposeApp(
    config: AppConfig,
    springContext: ConfigurableApplicationContext,
    workspaceLayoutService: WorkspaceLayoutService,
    lifecycle: ApplicationLifecycle,
    onCloseApplication: () -> Unit,
) {
    var windows by remember { mutableStateOf(restoreWorkspaceWindows(defaultWindows(), workspaceLayoutService.report().windows)) }
    var modal by remember { mutableStateOf<ComposeModal?>(null) }
    var commandPaletteVisible by remember { mutableStateOf(false) }
    var uiFontSize by remember { mutableStateOf(config.fontSize) }
    var themeMode by remember { mutableStateOf(config.uiThemeMode) }
    var settingsRevision by remember { mutableStateOf(0) }
    val workspaceFocusRequester = remember { FocusRequester() }
    val composeScope = rememberCoroutineScope()
    val toolEventBus = remember { springContext.getBean(ToolEventBus::class.java) }
    val inFlight = rememberInFlightState(toolEventBus)
    val panelServices =
        remember {
            ComposePanelServices(
                config = config,
                agentManager = springContext.getBean(AgentManager::class.java),
                llmProvider = springContext.getBean(LLMProvider::class.java),
                providerCatalogService = springContext.getBean(ProviderCatalogService::class.java),
                agentToolConfigService = springContext.getBean(AgentToolConfigService::class.java),
                toolRegistry = springContext.getBean(ToolRegistry::class.java),
                toolEventBus = springContext.getBean(ToolEventBus::class.java),
                workspaceFileService = springContext.getBean(WorkspaceFileService::class.java),
                canvasOperations = springContext.getBean(CanvasOperations::class.java),
                modalRequester = ComposeModalRequester { requested -> modal = requested },
                onSettingsChanged = {
                    uiFontSize = config.fontSize
                    themeMode = config.uiThemeMode
                    settingsRevision += 1
                },
                inFlight = inFlight,
                lifecycle = lifecycle,
            )
        }
    DisposableEffect(config) {
        val registration =
            config.addChangeListener { change ->
                if (change.key == AppConfig.KEY_UI_THEME_MODE) {
                    themeMode = config.uiThemeMode
                }
            }
        onDispose { registration.close() }
    }
    val toggleWindow: (String) -> Unit = { id ->
        windows = toggleWorkspacePanel(windows, id)
    }
    val activateWindow: (String) -> Unit = { id ->
        windows = windows.map { window -> if (window.id == id) window.copy(visible = true) else window }
    }
    val resizeWindow: (String, Int) -> Unit = { id, width ->
        windows =
            windows.map { window ->
                if (window.id == id) window.copy(preferredWidth = width) else window
            }
    }
    val reorderWindows: (List<ComposeWorkspaceWindow>) -> Unit = { visibleOrder ->
        val visibleIds = visibleOrder.map { it.id }.toSet()
        val visiblePanels = visibleOrder.mapNotNull { window -> windows.find { it.id == window.id } }
        val hiddenPanels = windows.filter { it.id !in visibleIds }
        val next = visiblePanels + hiddenPanels
        if (next != windows) {
            windows = next
        }
    }
    val updatePanelWidth: (String, Int) -> Unit = resizeWindow
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

    val darkTheme = isSystemInDarkTheme(themeMode)
    MaterialTheme(
        colorScheme = if (darkTheme) visualAgentDarkColorScheme() else visualAgentLightColorScheme(),
        typography = visualAgentTypography(uiFontSize),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                            }.focusable(),
                ) {
                    ComposeRail(
                        windows = windows,
                        onToggleWindow = toggleWindow,
                        onReorderWindows = reorderWindows,
                        onPanelWidthChanged = updatePanelWidth,
                        onCloseApplication = onCloseApplication,
                        modalRequester = panelServices.modalRequester,
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
                        val minPanelWidth = ComposeWorkspaceWindowBounds.MIN_WIDTH
                        val workspaceStates = windows.mapIndexed { index, window -> window.toWorkspaceWindowState(index) }
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
                                inFlight = inFlight.state.value,
                                onStopAll = {
                                    composeScope.launch {
                                        panelServices.agentManager.cancelAllRunningActions()
                                    }
                                },
                            )
                            ComposeSplitWorkspace(
                                windows = windows,
                                panelServices = panelServices,
                                onToggleWindow = toggleWindow,
                                onReorderWindows = reorderWindows,
                                onResizeWindow = resizeWindow,
                                minPanelWidth = minPanelWidth,
                                viewport = viewport,
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

private fun ComposeWorkspaceWindow.toWorkspaceWindowState(orderIndex: Int): WorkspaceWindowState =
    WorkspaceWindowState(
        id = id,
        order = orderIndex,
        visible = visible,
        preferredWidth = preferredWidth.toDouble(),
    )
