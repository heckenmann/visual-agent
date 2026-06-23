package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.panels.ApplicationSettingsPanel
import de.heckenmann.visualagent.ui.panels.ChatPanel
import de.heckenmann.visualagent.ui.panels.FilesPanel
import de.heckenmann.visualagent.ui.panels.SessionPanel
import de.heckenmann.visualagent.ui.panels.SubAgentsPanel
import de.heckenmann.visualagent.ui.panels.TodoPanel
import de.heckenmann.visualagent.ui.panels.canvas.CanvasPanel
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Pane
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Primary JavaFX stage that hosts navigation, workspace panels, status indicators, and backend wiring.
 *
 * The bean is lazy so JavaFX controls are constructed on the JavaFX application thread.
 *
 * Use cases: UC-0000001, UC-0000002, UC-0000007, UC-0000012, UC-0000034, UC-0000035,
 * UC-0000038, UC-0000039, UC-0000070.
 */
@Component
@Lazy // delay instantiation until requested on the JavaFX Application thread
@ConditionalOnProperty(name = ["visual-agent.ui.enabled"], havingValue = "true", matchIfMissing = true)
class MainWindow(
    private val agentManager: AgentManager,
    private val llmProvider: LLMProvider,
    private val toolEventBus: ToolEventBus,
    private val sessionPanel: SessionPanel,
    private val chatPanel: ChatPanel,
    private val subAgentsPanel: SubAgentsPanel,
    private val todoPanel: TodoPanel,
    private val canvasPanel: CanvasPanel,
    private val filesPanel: FilesPanel,
    private val applicationSettingsPanel: ApplicationSettingsPanel,
    private val providerCatalog: ProviderCatalogService,
    private val workspaceLayoutPersistence: WorkspaceLayoutPersistence,
    private val workspaceLayoutService: WorkspaceLayoutService,
) : Stage() {
    @FXML
    private lateinit var rootPane: BorderPane

    @FXML
    private lateinit var appIconImage: ImageView

    @FXML
    private lateinit var workspaceDesktop: Pane

    @FXML
    private lateinit var connectionStatus: Label

    @FXML
    private lateinit var agentsLabel: Label

    @FXML
    private lateinit var selectedModelLabel: Label

    private val statusBar = StatusBar()

    @FXML
    private lateinit var conversationBtn: Button

    @FXML
    private lateinit var sessionBtn: Button

    @FXML
    private lateinit var agentsBtn: Button

    @FXML
    private lateinit var planBtn: Button

    @FXML
    private lateinit var canvasBtn: Button

    @FXML
    private lateinit var filesBtn: Button

    @FXML
    private lateinit var closeApplicationBtn: Button

    @FXML
    private lateinit var settingsBtn: Button

    private val panelByButton = LinkedHashMap<Button, Node>()
    private lateinit var workspaceWindows: WorkspaceWindowManager

    private var activeButton: Button? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var configListenerRegistration: AutoCloseable? = null
    private var eventListenerRegistration: AutoCloseable? = null
    private val chatWiring =
        MainWindowChatWiring(agentManager, chatPanel, scope) {
            focusPanel(todoPanel, planBtn)
        }

    init {
        title = AppIdentity.DISPLAY_NAME
        AppIdentity.javaFxIcon()?.let { icons.add(it) }
        minWidth = 960.0
        minHeight = 680.0
        isResizable = true

        val root = FxmlLoader.load(this, "main-window.fxml")
        scene = Scene(root, 1400.0, 900.0)
        loadStyles()
        applyConfigToUi()
        checkConnection()
        // NOTE: Autonomous processing is NOT started automatically to avoid
        // unintentionally blocking the local environment. Call
        // agentManager.startAutonomousProcessing(true) explicitly when you want
        // SubAgents to pick up UX tasks.
    }

    @FXML
    private fun initialize() {
        AppIdentity.javaFxIcon()?.let { appIconImage.image = it }
        sessionPanel.setLlmProvider(llmProvider)
        rootPane.bottom = statusBar
        registerConfigObserver()

        panelByButton.clear()
        panelByButton.putAll(
            wireMainWorkspaceNavigation(
                mainWorkspaceButtons(),
                mainWorkspacePanels(),
            ) { panel, button -> focusPanel(panel, button) },
        )
        registerWorkspaceWindows()
        closeApplicationBtn.setOnAction { closeApplication() }

        MainWindowSubAgentWiring(agentManager, subAgentsPanel, chatPanel) { updateAgentCountUi() }.register()
        registerFilesPanelCallbacks()
        chatWiring.register()
        chatPanel.setConversationHistory(agentManager.getHistory())
        registerEventObservers()
        refreshTodoSummary()

        focusPanel(chatPanel, conversationBtn)
        setOnShown {
            chatPanel.focusInputAndScrollToBottom()
        }
        rootPane.sceneProperty().addListener { _, _, newScene ->
            if (newScene != null) {
                MainWindowNavigation(panelByButton) { panel, button -> focusPanel(panel, button) }
                    .setupKeyboardShortcuts(newScene)
            }
        }
    }

    /**
     * Subscribes the main window to application-wide configuration changes.
     *
     * The title model label, chat context, theme, and font size are derived from AppConfig
     * in one place so panels do not need to know who else depends on a changed setting.
     *
     * Use cases: UC-0000007, UC-0000037, UC-0000038.
     */
    private fun registerConfigObserver() {
        configListenerRegistration?.close()
        configListenerRegistration =
            AppConfig.instance.addChangeListener {
                Platform.runLater { applyConfigToUi() }
            }
        setOnHidden {
            configListenerRegistration?.close()
            configListenerRegistration = null
            eventListenerRegistration?.close()
            eventListenerRegistration = null
        }
    }

    /**
     * Subscribes to global tool and todo events.
     */
    private fun registerEventObservers() {
        eventListenerRegistration?.close()
        eventListenerRegistration =
            MainWindowToolWiring(agentManager, toolEventBus, chatPanel, chatWiring) {
                refreshTodoSummary()
            }.register()
    }

    /**
     * Applies the current AppConfig snapshot to UI elements that mirror global settings.
     */
    private fun applyConfigToUi() {
        val provider = providerCatalog.getProvider(providerCatalog.activeProviderId())
        selectedModelLabel.text = "${provider?.name ?: providerCatalog.activeProviderId()} · ${provider?.defaultModel.orEmpty()}"
        scene?.root?.style = "-fx-font-size: ${AppConfig.instance.fontSize}px;"
        Application.setUserAgentStylesheet(AppConfig.instance.getThemeStylesheet())
    }

    private fun registerWorkspaceWindows() {
        workspaceWindows =
            createMainWorkspaceWindows(
                workspaceDesktop,
                mainWorkspacePanels(),
            )
        workspaceLayoutService.bind(workspaceWindows, this)
        workspaceWindows.restore(workspaceLayoutPersistence.load())
        setOnCloseRequest {
            workspaceLayoutPersistence.save(workspaceWindows.snapshot())
        }
    }

    private fun mainWorkspacePanels(): MainWorkspacePanels =
        MainWorkspacePanels(
            chatPanel = chatPanel,
            sessionPanel = sessionPanel,
            subAgentsPanel = subAgentsPanel as Node,
            todoPanel = todoPanel,
            canvasPanel = canvasPanel,
            filesPanel = filesPanel,
            settingsPanel = applicationSettingsPanel,
        )

    private fun mainWorkspaceButtons(): MainWorkspaceButtons =
        MainWorkspaceButtons(
            conversationBtn = conversationBtn,
            sessionBtn = sessionBtn,
            agentsBtn = agentsBtn,
            planBtn = planBtn,
            canvasBtn = canvasBtn,
            filesBtn = filesBtn,
            settingsBtn = settingsBtn,
        )

    private fun focusPanel(
        panel: javafx.scene.Node,
        button: Button?,
    ) {
        workspaceWindows.focus(panel)

        activeButton?.styleClass?.remove("active")
        if (button != null) {
            button.styleClass.add("active")
        }
        activeButton = button
    }

    private fun closeApplication() {
        close()
        Platform.exit()
    }

    private fun loadStyles() {
        javaClass.getResource("/styles/application.css")?.toExternalForm()?.let { stylesheet ->
            scene.stylesheets.setAll(stylesheet)
        }
    }

    private fun registerFilesPanelCallbacks() {
        filesPanel.setOnFilesImported { imported ->
            if (imported.isEmpty()) return@setOnFilesImported
            val summary =
                imported.joinToString("\n") { file ->
                    "- ${file.relativePath} (${file.mimeType}, sha256=${file.sha256.take(12)}...)"
                }
            agentManager.appendSystemMessage("Imported workspace files:\n$summary")
            chatPanel.setConversationHistory(agentManager.getHistory())
            focusPanel(filesPanel, filesBtn)
        }
        filesPanel.setOnCanvasOpened {
            focusPanel(canvasPanel, canvasBtn)
        }
        chatPanel.setOnOpenFile {
            filesPanel.importWithDialog()
        }
    }

    /**
     * Checks provider connectivity asynchronously and updates connection-dependent UI state.
     */
    fun checkConnection() {
        scope.launch {
            doCheckConnection()
        }
    }

    private suspend fun doCheckConnection() {
        val isConnected =
            withContext(Dispatchers.IO) {
                llmProvider.checkConnection()
            }
        Platform.runLater {
            connectionStatus.text = if (isConnected) "Connected" else "Offline"
            connectionStatus.styleClass.removeAll("shell-status-online", "shell-status-offline")
            connectionStatus.styleClass.add(if (isConnected) "shell-status-online" else "shell-status-offline")
            val agents = agentManager.getSubAgents()
            statusBar.updateAgentCount(agents.count { it.status == AgentStatus.BUSY }, agents.size)
            updateAgentCountUi()
            applyConfigToUi()
        }
    }

    private fun updateAgentCountUi() {
        val count = agentManager.getSubAgents().size
        agentsLabel.text = if (count == 1) "1 agent" else "$count agents"
    }

    /**
     * Refreshes conversation header todo counters from persisted database state.
     */
    private fun refreshTodoSummary() {
        val summary = agentManager.getTodoSummaryFromDb()
        chatPanel.updateTodoSummary(
            total = summary.total,
            open = summary.open,
            inProgress = summary.inProgress,
            completed = summary.completed,
            cancelled = summary.cancelled,
        )
    }
}
