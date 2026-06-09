package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.control.ScrollPane
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Represents SessionPanel.
 */
@Component
@Lazy
class SessionPanel : Region() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @FXML
    private lateinit var providerSelector: ComboBox<String>

    @FXML
    private lateinit var modelSelector: ComboBox<String>

    @FXML
    private lateinit var modelSearchField: TextField

    @FXML
    private lateinit var favoritesOnlyToggle: CheckBox

    @FXML
    private lateinit var favoriteButton: Button

    @FXML
    private lateinit var refreshModelsButton: Button

    @FXML
    private lateinit var openAiApiKeyField: PasswordField

    @FXML
    private lateinit var openAiBaseUrlField: TextField

    @FXML
    private lateinit var openAiSettingsGroup: VBox

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
    private lateinit var userInstructionArea: TextArea

    @FXML
    private lateinit var scrollPane: ScrollPane
    private var llmProvider: LLMProvider? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val rootNode: Parent
    private val allModels = mutableListOf<String>()
    private val favoriteModels = linkedSetOf<String>()

    init {
        rootNode = FxmlLoader.load(this, "session-panel.fxml")
        children.add(rootNode)
        if (AppConfig.instance.favoriteModels.isNotBlank()) {
            favoriteModels.addAll(
                AppConfig.instance.favoriteModels
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() },
            )
        }
    }

    /**
     * Executes initialize.
     */
    @FXML
    fun initialize() {
        providerSelector.items.setAll("Ollama", "OpenAI")
        providerSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            if (selected != null) {
                AppConfig.instance.llmProvider = selected.lowercase()
                AppConfig.instance.save()
                updateProviderSpecificControls()
                refreshModels()
            }
        }

        openAiApiKeyField.textProperty().addListener { _, _, newVal ->
            AppConfig.instance.openAiApiKey = newVal ?: ""
            AppConfig.instance.save()
        }
        openAiBaseUrlField.textProperty().addListener { _, _, newVal ->
            AppConfig.instance.openAiBaseUrl = (newVal ?: "").ifBlank { "https://api.openai.com" }
            AppConfig.instance.save()
        }
        contextSlider.valueProperty().addListener { _, _, newVal ->
            contextValueLabel.text = newVal.toInt().toString()
            AppConfig.instance.contextLength = newVal.toInt()
            AppConfig.instance.save()
        }
        modelSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            if (selected != null) {
                AppConfig.instance.setActiveModel(selected)
                AppConfig.instance.save()
                refreshModelDetails(selected)
                updateFavoriteButton(selected)
            }
        }
        modelSearchField.textProperty().addListener { _, _, _ -> applyModelFilter() }
        favoritesOnlyToggle.selectedProperty().addListener { _, _, _ -> applyModelFilter() }
        favoriteButton.setOnAction { toggleFavoriteForSelectedModel() }
        refreshModelsButton.setOnAction { refreshModels() }

        streamingToggle.selectedProperty().addListener { _, _, newVal ->
            AppConfig.instance.streamingEnabled = newVal
            AppConfig.instance.save()
            logger.debug { "Streaming toggled: $newVal" }
        }

        thinkingToggle.selectedProperty().addListener { _, _, newVal ->
            AppConfig.instance.thinkingEnabled = newVal
            AppConfig.instance.save()
            logger.debug { "Thinking toggled: $newVal" }
        }
        autoCompactionToggle.selectedProperty().addListener { _, _, newVal ->
            AppConfig.instance.autoCompactionEnabled = newVal
            AppConfig.instance.save()
            logger.debug { "Auto compaction toggled: $newVal" }
        }

        loadLimitSpinner.valueFactory = IntegerSpinnerValueFactory(1, 1000, 50)
        maxParallelSubAgentsSpinner.valueFactory = IntegerSpinnerValueFactory(1, 20, 4)
        timeoutSpinner.valueFactory = IntegerSpinnerValueFactory(5, 600, 120)

        loadLimitSpinner.valueProperty().addListener { _, _, newVal ->
            AppConfig.instance.loadLimit = newVal
            AppConfig.instance.save()
            logger.debug { "Load limit changed: $newVal" }
        }

        maxParallelSubAgentsSpinner.valueProperty().addListener { _, _, newVal ->
            AppConfig.instance.maxParallelSubAgents = newVal
            AppConfig.instance.save()
            logger.debug { "Max parallel sub-agents changed: $newVal" }
        }

        timeoutSpinner.valueProperty().addListener { _, _, newVal ->
            AppConfig.instance.timeoutSeconds = newVal
            AppConfig.instance.save()
            logger.debug { "Timeout changed: $newVal" }
        }

        userInstructionArea.textProperty().addListener { _, _, newVal ->
            AppConfig.instance.userModelInstruction = newVal ?: ""
            AppConfig.instance.save()
            logger.debug { "User model instruction updated (length=${newVal?.length ?: 0})" }
        }

        contextSlider.value = AppConfig.instance.contextLength.toDouble()
        loadLimitSpinner.valueFactory.value = AppConfig.instance.loadLimit
        maxParallelSubAgentsSpinner.valueFactory.value = AppConfig.instance.maxParallelSubAgents
        timeoutSpinner.valueFactory.value = AppConfig.instance.timeoutSeconds
        streamingToggle.isSelected = AppConfig.instance.streamingEnabled
        thinkingToggle.isSelected = AppConfig.instance.thinkingEnabled
        autoCompactionToggle.isSelected = AppConfig.instance.autoCompactionEnabled
        userInstructionArea.text = AppConfig.instance.userModelInstruction
        providerSelector.selectionModel.select(if (AppConfig.instance.normalizedProvider() == "openai") "OpenAI" else "Ollama")
        openAiApiKeyField.text = AppConfig.instance.openAiApiKey
        openAiBaseUrlField.text = AppConfig.instance.openAiBaseUrl
        updateProviderSpecificControls()
        updateFavoriteButton(modelSelector.selectionModel.selectedItem)
    }

    /**
     * Returns the minimum panel width required for readable session controls.
     *
     * @param height Available height hint from JavaFX layout
     * @return Minimum width in pixels
     */
    override fun computeMinWidth(height: Double): Double = 360.0

    /**
     * Returns the minimum panel height that still leaves the scroll pane usable.
     *
     * @param width Available width hint from JavaFX layout
     * @return Minimum height in pixels
     */
    override fun computeMinHeight(width: Double): Double = 240.0

    /**
     * Resizes the loaded FXML root to this Region's current layout bounds.
     */
    override fun layoutChildren() {
        super.layoutChildren()
        rootNode.resizeRelocate(0.0, 0.0, width, height)
    }

    /**
     * Sets the provider used for fetching model data, then refreshes the model list and details.
     *
     * @param client The configured [LLMProvider] instance to use for API communication
     */
    fun setLlmProvider(client: LLMProvider) {
        this.llmProvider = client
        refreshModels()
    }

    /**
     * Fetches available models from the LLM provider and populates the model selector.
     * Selects the configured default model if present.
     */
    fun refreshModels() {
        val client = llmProvider ?: return
        modelSelector.isDisable = true
        refreshModelsButton.isDisable = true
        modelInfoLabel.text = "Loading models..."
        scope.launch {
            val models =
                try {
                    withContext(Dispatchers.IO) { client.getModels() }
                } catch (e: Exception) {
                    logger.warn { "refreshModels failed: ${e.message}" }
                    emptyList()
                }
            Platform.runLater {
                logger.debug { "refreshModels got ${models.size} models: $models" }
                allModels.clear()
                allModels.addAll(models)
                applyModelFilter(selectPreferred = true)
                modelSelector.isDisable = allModels.isEmpty()
                refreshModelsButton.isDisable = false
            }
        }
    }

    /**
     * Fetches details for the specified model and displays them in the model info label.
     *
     * @param modelName Name of the model to look up
     */
    fun refreshModelDetails(modelName: String) {
        val client = llmProvider ?: return
        modelInfoLabel.text = "Loading model details..."
        scope.launch {
            try {
                val details = withContext(Dispatchers.IO) { client.getModelDetails(modelName) }
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

    private fun updateProviderSpecificControls() {
        val openAiSelected = AppConfig.instance.normalizedProvider() == "openai"
        openAiSettingsGroup.isVisible = openAiSelected
        openAiSettingsGroup.isManaged = openAiSelected
    }

    private fun applyModelFilter(selectPreferred: Boolean = false) {
        val query = modelSearchField.text.trim().lowercase()
        val favoritesOnly = favoritesOnlyToggle.isSelected
        val filtered =
            allModels.filter { model ->
                val matchesQuery = query.isBlank() || model.lowercase().contains(query)
                val matchesFavorite = !favoritesOnly || favoriteModels.contains(model)
                matchesQuery && matchesFavorite
            }
        modelSelector.items.setAll(filtered)

        if (filtered.isEmpty()) {
            modelInfoLabel.text =
                if (allModels.isEmpty()) {
                    "No models available. Check Ollama connection."
                } else {
                    "No models match the current filter."
                }
            return
        }

        val currentModel = AppConfig.instance.activeModel()
        when {
            filtered.contains(currentModel) -> modelSelector.selectionModel.select(currentModel)
            selectPreferred -> modelSelector.selectionModel.select(0)
            modelSelector.selectionModel.selectedIndex < 0 -> modelSelector.selectionModel.select(0)
        }

        updateFavoriteButton(modelSelector.selectionModel.selectedItem)
    }

    private fun toggleFavoriteForSelectedModel() {
        val selected = modelSelector.selectionModel.selectedItem ?: return
        if (favoriteModels.contains(selected)) {
            favoriteModels.remove(selected)
        } else {
            favoriteModels.add(selected)
        }
        AppConfig.instance.favoriteModels = favoriteModels.joinToString(",")
        AppConfig.instance.save()
        updateFavoriteButton(selected)
        applyModelFilter(selectPreferred = false)
    }

    private fun updateFavoriteButton(selected: String?) {
        val isFavorite = selected != null && favoriteModels.contains(selected)
        favoriteButton.text = if (isFavorite) "★" else "☆"
        favoriteButton.isDisable = selected == null
        favoriteButton.tooltip = javafx.scene.control.Tooltip(if (isFavorite) "Remove from favorites" else "Add to favorites")
    }
}
