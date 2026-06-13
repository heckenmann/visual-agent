package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.FxmlLoader
import de.heckenmann.visualagent.ui.panels.session.SessionModelController
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
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Represents SessionPanel.
 */
@Component
@Lazy
class SessionPanel : Region() {
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
    private val rootNode: Parent
    private lateinit var modelController: SessionModelController

    init {
        rootNode = FxmlLoader.load(this, "session-panel.fxml")
        children.add(rootNode)
    }

    /**
     * Executes initialize.
     */
    @FXML
    fun initialize() {
        modelController =
            SessionModelController(
                modelSelector = modelSelector,
                modelSearchField = modelSearchField,
                favoritesOnlyToggle = favoritesOnlyToggle,
                favoriteButton = favoriteButton,
                refreshModelsButton = refreshModelsButton,
                openAiSettingsGroup = openAiSettingsGroup,
                modelInfoLabel = modelInfoLabel,
            )
        providerSelector.items.setAll("Ollama", "OpenAI")
        providerSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            if (selected != null) {
                AppConfig.instance.llmProvider = selected.lowercase()
                AppConfig.instance.save()
                modelController.updateProviderSpecificControls()
                modelController.refreshModels()
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
                modelController.refreshModelDetails(selected)
                modelController.updateFavoriteButton(selected)
            }
        }
        modelSearchField.textProperty().addListener { _, _, _ -> modelController.applyModelFilter() }
        favoritesOnlyToggle.selectedProperty().addListener { _, _, _ -> modelController.applyModelFilter() }
        favoriteButton.setOnAction { modelController.toggleFavoriteForSelectedModel() }
        refreshModelsButton.setOnAction { modelController.refreshModels() }

        streamingToggle.selectedProperty().addListener { _, _, newVal ->
            AppConfig.instance.streamingEnabled = newVal
            AppConfig.instance.save()
        }

        thinkingToggle.selectedProperty().addListener { _, _, newVal ->
            AppConfig.instance.thinkingEnabled = newVal
            AppConfig.instance.save()
        }
        autoCompactionToggle.selectedProperty().addListener { _, _, newVal ->
            AppConfig.instance.autoCompactionEnabled = newVal
            AppConfig.instance.save()
        }

        loadLimitSpinner.valueFactory = IntegerSpinnerValueFactory(1, 1000, 50)
        maxParallelSubAgentsSpinner.valueFactory = IntegerSpinnerValueFactory(1, 20, 4)
        timeoutSpinner.valueFactory = IntegerSpinnerValueFactory(5, 600, 120)

        loadLimitSpinner.valueProperty().addListener { _, _, newVal ->
            AppConfig.instance.loadLimit = newVal
            AppConfig.instance.save()
        }

        maxParallelSubAgentsSpinner.valueProperty().addListener { _, _, newVal ->
            AppConfig.instance.maxParallelSubAgents = newVal
            AppConfig.instance.save()
        }

        timeoutSpinner.valueProperty().addListener { _, _, newVal ->
            AppConfig.instance.timeoutSeconds = newVal
            AppConfig.instance.save()
        }

        userInstructionArea.textProperty().addListener { _, _, newVal ->
            AppConfig.instance.userModelInstruction = newVal ?: ""
            AppConfig.instance.save()
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
        modelController.updateProviderSpecificControls()
        modelController.updateFavoriteButton(modelSelector.selectionModel.selectedItem)
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
     * @param client The configured [de.heckenmann.visualagent.agent.LLMProvider] instance to use for API communication
     */
    fun setLlmProvider(client: de.heckenmann.visualagent.agent.LLMProvider) {
        modelController.setLlmProvider(client)
    }
}
