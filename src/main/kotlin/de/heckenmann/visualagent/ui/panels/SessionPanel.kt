package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.OllamaClient
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import javafx.scene.layout.Region
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mu.KotlinLogging

/**
 * Session configuration panel for model selection, context settings, and behavior toggles.
 *
 * Loads its layout from `session-panel.fxml` and wires all FXML controls to event handlers.
 * Requires a [OllamaClient] via [setOllamaClient] to populate model lists and details.
 */
class SessionPanel : Region() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @FXML
    private lateinit var modelSelector: ComboBox<String>

    @FXML
    private lateinit var contextSlider: Slider

    @FXML
    private lateinit var contextValueLabel: Label

    @FXML
    private lateinit var streamingToggle: CheckBox

    @FXML
    private lateinit var thinkingToggle: CheckBox

    @FXML
    private lateinit var autoCompactionToggle: CheckBox

    @FXML
    private lateinit var loadLimitSpinner: Spinner<Int>

    @FXML
    private lateinit var maxParallelSubAgentsSpinner: Spinner<Int>

    @FXML
    private lateinit var timeoutSpinner: Spinner<Int>

    @FXML
    private lateinit var modelInfoLabel: Label

    @FXML
    private lateinit var scrollPane: ScrollPane

    private var ollamaClient: LLMProvider? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        val root = FxmlLoader.load(this, "session-panel.fxml")
        children.add(root)
    }

    /**
     * Called automatically by the FXMLLoader after all FXML fields are injected.
     * Sets up event handlers and spinner value factories.
     */
    @FXML
    fun initialize() {
        contextSlider.valueProperty().addListener { _, _, newVal ->
            contextValueLabel.text = newVal.toInt().toString()
        }

        modelSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            if (selected != null) {
                AppConfig.instance.ollamaModel = selected
                refreshModelDetails(selected)
            }
        }

        streamingToggle.selectedProperty().addListener { _, _, newVal ->
            logger.debug { "Streaming toggled: $newVal" }
        }

        thinkingToggle.selectedProperty().addListener { _, _, newVal ->
            logger.debug { "Thinking toggled: $newVal" }
        }

        autoCompactionToggle.selectedProperty().addListener { _, _, newVal ->
            logger.debug { "Auto compaction toggled: $newVal" }
        }

        loadLimitSpinner.valueFactory = IntegerSpinnerValueFactory(1, 1000, 50)
        maxParallelSubAgentsSpinner.valueFactory = IntegerSpinnerValueFactory(1, 20, 4)
        timeoutSpinner.valueFactory = IntegerSpinnerValueFactory(5, 600, 120)

        loadLimitSpinner.valueProperty().addListener { _, _, newVal ->
            logger.debug { "Load limit changed: $newVal" }
        }

        maxParallelSubAgentsSpinner.valueProperty().addListener { _, _, newVal ->
            logger.debug { "Max parallel sub-agents changed: $newVal" }
        }

        timeoutSpinner.valueProperty().addListener { _, _, newVal ->
            logger.debug { "Timeout changed: $newVal" }
        }

        contextSlider.value = 4096.0
        streamingToggle.isSelected = true
        autoCompactionToggle.isSelected = true
    }

    /**
     * Sets the Ollama client used for fetching model data, then refreshes the model list and details.
     *
     * @param client The [OllamaClient] instance to use for API communication
     */
    fun setOllamaClient(client: LLMProvider) {
        this.ollamaClient = client
        refreshModels()
    }

    /**
     * Fetches available models from the LLM provider and populates the model selector.
     * Selects the configured default model if present.
     */
    fun refreshModels() {
        val client = ollamaClient ?: return
        scope.launch {
            val models =
                try {
                    client.getModels()
                } catch (e: Exception) {
                    logger.warn { "refreshModels failed: ${e.message}" }
                    emptyList()
                }
            Platform.runLater {
                logger.debug { "refreshModels got ${models.size} models: $models" }
                val currentModel = AppConfig.instance.ollamaModel
                modelSelector.items.setAll(models)
                if (models.contains(currentModel)) {
                    modelSelector.selectionModel.select(currentModel)
                } else if (models.isNotEmpty()) {
                    modelSelector.selectionModel.select(0)
                }
            }
        }
    }

    /**
     * Fetches details for the specified model and displays them in the model info label.
     *
     * @param modelName Name of the model to look up
     */
    fun refreshModelDetails(modelName: String) {
        val client = ollamaClient ?: return
        scope.launch {
            try {
                val details = client.getModelDetails(modelName)
                Platform.runLater {
                    val sb = StringBuilder()
                    sb.appendLine("Model: ${details.model}")
                    sb.appendLine("Modified: ${details.modifiedAt}")
                    if (details.details != null) {
                        sb.appendLine("Family: ${details.details.family ?: "unknown"}")
                        sb.appendLine("Size: ${details.details.parameterSize ?: "unknown"}")
                        sb.appendLine("Format: ${details.details.format ?: "unknown"}")
                    }
                    modelInfoLabel.text = sb.toString().ifEmpty { "No details available" }
                }
            } catch (e: Exception) {
                Platform.runLater {
                    modelInfoLabel.text = "Error loading details: ${e.message}"
                }
            }
        }
    }
}
