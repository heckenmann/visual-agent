package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.config.AppConfig
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
import javafx.scene.control.ChoiceDialog
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Lazy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Component
@Lazy // delay instantiation until requested on the JavaFX Application thread
@ConditionalOnProperty(name = ["visual-agent.ui.enabled"], havingValue = "true", matchIfMissing = true)
class MainWindow(
    private val agentManager: AgentManager,
    private val llmProvider: LLMProvider,
) : Stage() {
    companion object {
        /**
         * Testable predicate: whether the global back button should be shown.
         * Uses identity comparison so callers can pass any object representing panels.
         */
        fun shouldShowBack(activePanel: Any, chatPanel: Any): Boolean = activePanel !== chatPanel
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

    private val sessionPanel = SessionPanel()
    private val chatPanel = ChatPanel()
    private val subAgentsPanel = SubAgentsPanel()
    private val todoPanel = TodoPanel(agentManager.todoManager)
    private val canvasPanel = CanvasPanel()
    private val applicationSettingsPanel = ApplicationSettingsPanel()
    private val panelByButton = LinkedHashMap<Button, Node>()

    private var activeButton: Button? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentConnectionState = false
    private var configListenerRegistration: AutoCloseable? = null

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
        sessionPanel.setOllamaClient(llmProvider)
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

        // Wire SubAgentsPanel UI actions to AgentManager backend
        subAgentsPanel.agentActionCallback = { action, agentId ->
            when (action) {
                "update" -> {
                    // UI already mutated the agent model. Persist changes to AgentManager
                    val uiAgent = agentManager.getSubAgent(agentId)
                    if (uiAgent != null) {
                        // attempt to update based on UI changes (name/role/config may have been changed on UI instance)
                        // To keep consistency, refresh UI card from AgentManager after update
                        agentManager.updateAgent(agentId, name = uiAgent.name, role = uiAgent.role, config = uiAgent.config)
                        Platform.runLater { updateAgentCountUi() }
                    } else {
                        println("[MainWindow] update: agent not found in manager: $agentId")
                    }
                }
                "delete" -> {
                    if (agentManager.deleteAgent(agentId)) {
                        Platform.runLater { updateAgentCountUi() }
                    }
                }
                "run" -> {
                    // assign first pending todo to this agent if available
                    val pending = agentManager.todoManager.getPending().firstOrNull()
                    if (pending != null) {
                        val ok = agentManager.assignTodoToAgent(pending.id, agentId)
                        if (!ok) println("[MainWindow] Failed to assign todo ${pending.id} to agent $agentId")
                    } else {
                        println("[MainWindow] No pending todos to run")
                    }
                }
            }
        }

        // Create agent flow: create via AgentManager and add to panel
        subAgentsPanel.onCreateAgent = { name, role, template ->
            val created = agentManager.createAgent(name, role, template)
            Platform.runLater {
                subAgentsPanel.addAgent(created)
                updateAgentCountUi()
            }
        }

        // Initialize the SubAgentsPanel with agents from AgentManager (override panel defaults)
        subAgentsPanel.setAgents(agentManager.getSubAgents())

        chatPanel.setOnSendMessage { text ->
            scope.launch {
                val startedAt = System.nanoTime()
                try {
                    val response = withContext(Dispatchers.IO) {
                        agentManager.sendMessage(text)
                    }
                    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                    Platform.runLater {
                        chatPanel.addAssistantMessage(response)
                        chatPanel.updateResponseMetrics(elapsedMs)
                    }
                } catch (e: Exception) {
                    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                    Platform.runLater {
                        chatPanel.addAssistantMessage("Error: ${e.message}")
                        chatPanel.updateResponseMetrics(elapsedMs)
                    }
                }
            }
        }
        chatPanel.setOnClearConversation {
            agentManager.clearHistory()
        }
        chatPanel.setConversationHistory(agentManager.getHistory())

        AgentManager.setAgentCallback { agentId, message ->
            Platform.runLater {
                // Mirror messages into chat panel
                chatPanel.addAssistantMessage("[$agentId]: $message")
                // Also update agent card status from AgentManager authoritative state
                val a = agentManager.getSubAgent(agentId)
                if (a != null) {
                    subAgentsPanel.updateAgentStatus(agentId, a.status, a.currentTask)
                }
                updateAgentCountUi()
            }
        }

        switchPanel(chatPanel, conversationBtn)
        rootPane.sceneProperty().addListener { _, _, newScene ->
            if (newScene != null) {
                setupKeyboardShortcuts(newScene)
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
        configListenerRegistration = AppConfig.instance.addChangeListener {
            Platform.runLater { applyConfigToUi() }
        }
        setOnHidden {
            configListenerRegistration?.close()
            configListenerRegistration = null
        }
    }

    /**
     * Applies the current AppConfig snapshot to UI elements that mirror global settings.
     */
    private fun applyConfigToUi() {
        selectedModelLabel.text = " ${AppConfig.instance.ollamaModel}"
        scene?.root?.style = "-fx-font-size: ${AppConfig.instance.fontSize}px;"
        Application.setUserAgentStylesheet(AppConfig.instance.getThemeStylesheet())
        chatPanel.updateSessionContext(
            model = AppConfig.instance.ollamaModel,
            connected = currentConnectionState,
            agentCount = agentManager.getSubAgents().size,
        )
    }

    private fun switchPanel(panel: javafx.scene.Node, button: Button?) {
        chatArea.center = panel

        activeButton?.styleClass?.remove("active")
        if (button != null) {
            button.styleClass.add("active")
        }
        activeButton = button
    }

    private fun setupKeyboardShortcuts(targetScene: Scene) {
        val shortcuts =
            mapOf(
                KeyCode.DIGIT1 to conversationBtn,
                KeyCode.DIGIT2 to sessionBtn,
                KeyCode.DIGIT3 to agentsBtn,
                KeyCode.DIGIT4 to planBtn,
                KeyCode.DIGIT5 to canvasBtn,
                KeyCode.DIGIT6 to settingsBtn,
            )

        shortcuts.forEach { (keyCode, button) ->
            targetScene.accelerators[KeyCodeCombination(keyCode, KeyCombination.SHORTCUT_DOWN)] = Runnable {
                panelByButton[button]?.let { panel -> switchPanel(panel, button) }
            }
        }

        targetScene.accelerators[KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN)] = Runnable {
            openCommandPalette()
        }
    }

    private fun openCommandPalette() {
        val options = listOf("Conversation", "Session", "Agents", "Todos", "Canvas", "Settings")
        val dialog = ChoiceDialog(options.first(), options)
        dialog.title = "Command Palette"
        dialog.headerText = null
        dialog.contentText = "Switch panel:"
        val selected = dialog.showAndWait()
        if (selected.isPresent) {
            when (selected.get()) {
                "Conversation" -> switchPanel(chatPanel, conversationBtn)
                "Session" -> switchPanel(sessionPanel, sessionBtn)
                "Agents" -> switchPanel(subAgentsPanel as Node, agentsBtn)
                "Todos" -> switchPanel(todoPanel, planBtn)
                "Canvas" -> switchPanel(canvasPanel, canvasBtn)
                "Settings" -> switchPanel(applicationSettingsPanel, settingsBtn)
            }
        }
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
        val isConnected = withContext(Dispatchers.IO) {
            llmProvider.checkConnection()
        }
        Platform.runLater {
            currentConnectionState = isConnected
            connectionStatus.text = if (isConnected) " Connected" else " Disconnected"
            updateAgentCountUi()
            applyConfigToUi()
        }
    }

    private fun updateAgentCountUi() {
        val count = agentManager.getSubAgents().size
        agentsLabel.text = " $count"
    }
}
