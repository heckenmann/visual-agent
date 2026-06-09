package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.StatusBar
import de.heckenmann.visualagent.ui.panels.ApplicationSettingsPanel
import de.heckenmann.visualagent.ui.panels.CanvasPanel
import de.heckenmann.visualagent.ui.panels.ChatPanel
import de.heckenmann.visualagent.ui.panels.SessionPanel
import de.heckenmann.visualagent.ui.panels.SubAgentsPanel
import de.heckenmann.visualagent.ui.panels.TodoPanel
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
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
 * Represents MainWindow.
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
    private val applicationSettingsPanel: ApplicationSettingsPanel,
) : Stage() {
    companion object {
        /** Testable predicate for global back-button visibility. */
        fun shouldShowBack(
            activePanel: Any,
            chatPanel: Any,
        ): Boolean = activePanel !== chatPanel
    }

    @FXML
    private lateinit var rootPane: BorderPane

    @FXML
    private lateinit var titleBar: HBox

    @FXML
    private lateinit var appIconImage: ImageView

    @FXML
    private lateinit var iconRail: VBox

    @FXML
    private lateinit var chatArea: BorderPane

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
    private lateinit var settingsBtn: Button

    private val panelByButton = LinkedHashMap<Button, Node>()

    private var activeButton: Button? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentConnectionState = false
    private var configListenerRegistration: AutoCloseable? = null
    private var eventListenerRegistration: AutoCloseable? = null
    private val chatWiring =
        MainWindowChatWiring(agentManager, chatPanel, scope) {
            switchPanel(todoPanel, planBtn)
        }

    init {
        title = AppIdentity.DISPLAY_NAME
        AppIdentity.javaFxIcon()?.let { icons.add(it) }
        minWidth = 1200.0
        minHeight = 800.0
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

        conversationBtn.setOnAction { switchPanel(chatPanel, conversationBtn) }
        sessionBtn.setOnAction { switchPanel(sessionPanel, sessionBtn) }
        agentsBtn.setOnAction { switchPanel(subAgentsPanel as Node, agentsBtn) }
        planBtn.setOnAction { switchPanel(todoPanel, planBtn) }
        canvasBtn.setOnAction { switchPanel(canvasPanel, canvasBtn) }
        settingsBtn.setOnAction { switchPanel(applicationSettingsPanel, settingsBtn) }
        panelByButton.clear()
        panelByButton[conversationBtn] = chatPanel
        panelByButton[sessionBtn] = sessionPanel
        panelByButton[agentsBtn] = subAgentsPanel as Node
        panelByButton[planBtn] = todoPanel
        panelByButton[canvasBtn] = canvasPanel
        panelByButton[settingsBtn] = applicationSettingsPanel

        MainWindowSubAgentWiring(agentManager, subAgentsPanel, chatPanel) { updateAgentCountUi() }.register()
        chatWiring.register()
        chatPanel.setConversationHistory(agentManager.getHistory())
        registerEventObservers()
        statusBar.setOnReconnect { checkConnection() }
        refreshTodoSummary()

        switchPanel(chatPanel, conversationBtn)
        setOnShown {
            chatPanel.focusInputAndScrollToBottom()
        }
        rootPane.sceneProperty().addListener { _, _, newScene ->
            if (newScene != null) {
                MainWindowNavigation(panelByButton) { panel, button -> switchPanel(panel, button) }
                    .setupKeyboardShortcuts(newScene)
            }
        }
    }

    /**
     * Subscribes the main window to application-wide configuration changes.
     *
     * The title model label, chat context, theme, and font size are derived from AppConfig
     * in one place so panels do not need to know who else depends on a changed setting.
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
        selectedModelLabel.text = " ${AppConfig.instance.normalizedProvider()}: ${AppConfig.instance.activeModel()}"
        statusBar.updateModel(AppConfig.instance.activeModel())
        scene?.root?.style = "-fx-font-size: ${AppConfig.instance.fontSize}px;"
        Application.setUserAgentStylesheet(AppConfig.instance.getThemeStylesheet())
    }

    private fun switchPanel(
        panel: javafx.scene.Node,
        button: Button?,
    ) {
        chatArea.center = panel

        activeButton?.styleClass?.remove("active")
        if (button != null) {
            button.styleClass.add("active")
        }
        activeButton = button
    }

    private fun loadStyles() {
        javaClass.getResource("/styles/application.css")?.toExternalForm()?.let { stylesheet ->
            scene.stylesheets.setAll(stylesheet)
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
            currentConnectionState = isConnected
            connectionStatus.text = if (isConnected) " Connected" else " Disconnected"
            statusBar.updateConnectionStatus(isConnected)
            val agents = agentManager.getSubAgents()
            statusBar.updateAgentCount(agents.count { it.status == AgentStatus.BUSY }, agents.size)
            updateAgentCountUi()
            applyConfigToUi()
        }
    }

    private fun updateAgentCountUi() {
        val count = agentManager.getSubAgents().size
        agentsLabel.text = " $count"
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
