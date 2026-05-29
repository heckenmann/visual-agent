package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.OllamaClient
import de.heckenmann.visualagent.ui.panels.ApplicationSettingsPanel
import de.heckenmann.visualagent.ui.panels.CanvasPanel
import de.heckenmann.visualagent.ui.panels.ChatPanel
import de.heckenmann.visualagent.ui.panels.SessionPanel
import de.heckenmann.visualagent.ui.panels.SubAgentsPanel
import de.heckenmann.visualagent.ui.panels.TodoPanel
import de.heckenmann.visualagent.ui.StatusBar
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.stage.StageStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Lazy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Component
@Lazy // delay instantiation until requested on the JavaFX Application thread
@ConditionalOnProperty(name = ["visual-agent.ui.enabled"], havingValue = "true", matchIfMissing = true)
class MainWindow(
    private val agentManager: AgentManager,
    private val ollamaClient: OllamaClient,
) : Stage() {

    @FXML
    private lateinit var rootPane: BorderPane

    @FXML
    private lateinit var titleBar: HBox

    @FXML
    private lateinit var iconRail: VBox

    @FXML
    private lateinit var chatArea: BorderPane

    @FXML
    private lateinit var connectionStatus: Label

    @FXML
    private lateinit var agentsLabel: Label

    @FXML
    private lateinit var minimizeButton: Button

    @FXML
    private lateinit var maximizeButton: Button

    @FXML
    private lateinit var closeButton: Button

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
    private val statusBar = StatusBar()

    private var activeButton: Button? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isMaximized = false
    private var savedX = 0.0
    private var savedY = 0.0
    private var savedWidth = 0.0
    private var savedHeight = 0.0

    private var dragStartX = 0.0
    private var dragStartY = 0.0

    init {
        initStyle(StageStyle.UNDECORATED)
        title = "Visual Agent"
        minWidth = 1200.0
        minHeight = 800.0

        val root = FxmlLoader.load(this, "main-window.fxml")
        scene = Scene(root, 1400.0, 900.0)
        loadStyles()
        checkConnection()
        // NOTE: Autonomous processing is NOT started automatically to avoid
        // unintentionally blocking the local environment. Call
        // agentManager.startAutonomousProcessing(true) explicitly when you want
        // SubAgents to pick up UX tasks.
    }

    @FXML
    private fun initialize() {
        sessionPanel.setOllamaClient(ollamaClient)
        rootPane.bottom = statusBar

        closeButton.setOnAction { close() }
        minimizeButton.setOnAction { isIconified = true }
        maximizeButton.setOnAction { toggleMaximize() }

        sessionBtn.setOnAction { switchPanel(sessionPanel, sessionBtn) }
        agentsBtn.setOnAction { switchPanel(subAgentsPanel as Node, agentsBtn) }
        planBtn.setOnAction { switchPanel(todoPanel, planBtn) }
        canvasBtn.setOnAction { switchPanel(canvasPanel, canvasBtn) }
        settingsBtn.setOnAction { switchPanel(applicationSettingsPanel, settingsBtn) }

        chatPanel.setOnSendMessage { text ->
            scope.launch {
                try {
                    val response = agentManager.sendMessage(text)
                    Platform.runLater { chatPanel.addAssistantMessage(response) }
                } catch (e: Exception) {
                    Platform.runLater { chatPanel.addAssistantMessage("Error: ${e.message}") }
                }
            }
        }

        AgentManager.setAgentCallback { agentId, message ->
            Platform.runLater {
                chatPanel.addAssistantMessage("[$agentId]: $message")
            }
        }

        switchPanel(chatPanel, null)
        setupWindowDrag()
    }

    private fun switchPanel(panel: javafx.scene.Node, button: Button?) {
        chatArea.center = panel
        activeButton?.styleClass?.remove("active")
        if (button != null) {
            button.styleClass.add("active")
        }
        activeButton = button
    }

    private fun loadStyles() {
        val css = javaClass.getResource("/styles/application.css")?.toExternalForm()
        if (css != null) {
            scene.stylesheets.add(css)
        }
    }

    private fun setupWindowDrag() {
        titleBar.setOnMousePressed { event ->
            dragStartX = event.sceneX
            dragStartY = event.sceneY
        }

        titleBar.setOnMouseDragged { event ->
            if (!isMaximized) {
                x = event.screenX - dragStartX
                y = event.screenY - dragStartY
            }
        }

        titleBar.setOnMouseReleased {
            persistBounds()
        }
    }

    private fun toggleMaximize() {
        if (isMaximized) {
            this.x = savedX
            this.y = savedY
            this.width = savedWidth
            this.height = savedHeight
            isMaximized = false
        } else {
            savedX = this.x
            savedY = this.y
            savedWidth = this.width
            savedHeight = this.height
            this.x = 0.0
            this.y = 0.0
            this.width = 1920.0
            this.height = 1080.0
            isMaximized = true
        }
    }

    private fun persistBounds() {
        if (!isMaximized) {
            savedX = this.x
            savedY = this.y
            savedWidth = this.width
            savedHeight = this.height
        }
    }

    fun checkConnection() {
        scope.launch {
            doCheckConnection()
        }
    }

    private suspend fun doCheckConnection() {
        // OllamaClient checks connection via /api/tags or similar
        // connected = ...
        val isConnected = true // Mock for now, Spring AI handles connectivity differently
        Platform.runLater {
            statusBar.updateConnectionStatus(isConnected)
            agentsLabel.text = " ${agentManager.getSubAgents().size}"
        }
    }
}
