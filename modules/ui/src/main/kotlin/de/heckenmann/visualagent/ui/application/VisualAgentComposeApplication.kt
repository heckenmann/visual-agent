@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.application

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
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.protocol.SettingsSnapshot
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Renders the ready workspace using only the transport boundary supplied by the desktop host. */
@Composable
fun VisualAgentComposeApp(
    deps: ComposeApplicationDependencies,
    onCloseApplication: () -> Unit,
    persistedWindows: List<LayoutWindowState>,
) {
    var windows by remember { mutableStateOf(restoreWorkspaceWindows(defaultWindows(), persistedWindows)) }
    var modal by remember { mutableStateOf<ComposeModal?>(null) }
    var commandPaletteVisible by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(SettingsSnapshot()) }
    var settingsLoaded by remember { mutableStateOf(false) }
    var settingsRevision by remember { mutableStateOf(0) }
    val workspaceFocusRequester = remember { FocusRequester() }
    val composeScope = rememberCoroutineScope()
    val inFlight = rememberInFlightState(deps.applicationPort.activity)
    val panelServices =
        remember {
            ComposePanelServices(
                settings = deps.applicationPort.settings,
                agents = deps.applicationPort.agents,
                providers = deps.applicationPort.providers,
                activity = deps.applicationPort.activity,
                workspaceFiles = deps.applicationPort.workspaceFiles,
                canvas = deps.applicationPort.canvas,
                conversation = deps.applicationPort.conversation,
                clientImagePort = deps.clientImagePort,
                todos = deps.applicationPort.todos,
                modalRequester = ComposeModalRequester { requested -> modal = requested },
                onSettingsChanged = {
                    composeScope.launch {
                        settings = deps.applicationPort.settings.snapshotAsync()
                        settingsLoaded = true
                        settingsRevision += 1
                    }
                },
                inFlight = inFlight,
                lifecycle = deps.applicationPort.lifecycle,
            )
        }
    DisposableEffect(deps.applicationPort.settings) {
        val registration =
            deps.applicationPort.settings.addChangeListener { next ->
                composeScope.launch {
                    settings = next
                    settingsLoaded = true
                }
            }
        onDispose { registration.close() }
    }
    LaunchedEffect(deps.applicationPort.settings) {
        settings = deps.applicationPort.settings.snapshotAsync()
        settingsLoaded = true
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
    val commands =
        windows.map { window ->
            ComposeCommand(
                id = "open-${window.id}",
                title = "Open ${window.title}",
                description = window.subtitle,
            ) {
                activateWindow(window.id)
            }
        } + ComposeCommand("close-application", "Close application", "Close Visual Agent and persist workspace state", onCloseApplication)
    LaunchedEffect(Unit) {
        workspaceFocusRequester.requestFocus()
    }
    RegisterAgentStatusCallback(inFlight, deps.applicationPort.activity, deps.applicationPort.todos)
    DisposableEffect(deps.applicationPort.layout) {
        val handle =
            deps.applicationPort.layout.addWindowStateListener { states ->
                composeScope.launch {
                    windows = restoreWorkspaceWindows(windows, states)
                }
            }
        onDispose { handle.close() }
    }

    val darkTheme = isSystemInDarkTheme(settings.uiThemeMode)
    ApplyVisualAgentUiScale(settings.uiScalePercent) {
        MaterialTheme(
            colorScheme = if (darkTheme) visualAgentDarkColorScheme() else visualAgentLightColorScheme(),
            typography = visualAgentTypography(settings.fontSize),
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
                            onPanelWidthChanged = resizeWindow,
                            showPanelLabels = settings.showPanelLabels,
                            onTogglePanelLabels = {
                                if (settingsLoaded) {
                                    val next = settings.copy(showPanelLabels = !settings.showPanelLabels)
                                    settings = next
                                    composeScope.launch {
                                        withContext(Dispatchers.IO) { deps.applicationPort.settings.save(next) }
                                    }
                                }
                            },
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
                            val workspaceStates = windows.mapIndexed { index, window -> window.toLayoutWindowState(index) }
                            deps.applicationPort.layout.bind(
                                stage = LayoutSize(width = viewport.width.toDouble(), height = viewport.height.toDouble()),
                                desktop = LayoutSize(width = viewport.width.toDouble(), height = viewport.height.toDouble()),
                                windows = workspaceStates,
                            )
                            LaunchedEffect(workspaceStates) {
                                deps.applicationPort.layout.applyWindowStates(workspaceStates, notifyListeners = false)
                            }
                            val activeProvider =
                                remember(settingsRevision) {
                                    panelServices.providers.getProvider(panelServices.providers.activeProviderId())
                                }
                            Column(modifier = Modifier.fillMaxSize()) {
                                ComposeWorkspaceHeader(
                                    providerName = activeProvider?.id ?: panelServices.providers.activeProviderId(),
                                    modelName = panelServices.providers.activeModelId(),
                                    beanDefinitionCount = deps.beanDefinitionCount,
                                    inFlight = inFlight.state.value,
                                    onStopAll = {
                                        composeScope.launch {
                                            deps.applicationPort.cancelActiveWork()
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
}

private fun ComposeWorkspaceWindow.toLayoutWindowState(orderIndex: Int): LayoutWindowState =
    LayoutWindowState(
        id = id,
        order = orderIndex,
        visible = visible,
        preferredWidth = preferredWidth.toDouble(),
    )
