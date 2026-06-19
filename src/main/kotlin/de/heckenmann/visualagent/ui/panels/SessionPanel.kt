package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.FxmlLoader
import de.heckenmann.visualagent.ui.panels.session.ProviderProfileDialog
import de.heckenmann.visualagent.ui.panels.session.SessionModelController
import de.heckenmann.visualagent.ui.panels.session.SessionProviderSettingsBinder
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
 * Session settings panel for provider/model selection, generation options, and runtime limits.
 */
@Component
@Lazy
class SessionPanel(
    private val providerCatalog: ProviderCatalogService,
) : Region() {
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
    private lateinit var addProviderButton: Button

    @FXML
    private lateinit var editProviderButton: Button

    @FXML
    private lateinit var deleteProviderButton: Button

    @FXML
    private lateinit var ollamaApiKeyField: PasswordField

    @FXML
    private lateinit var ollamaBaseUrlField: TextField

    @FXML
    private lateinit var ollamaSettingsGroup: VBox

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
    private lateinit var providerSettingsBinder: SessionProviderSettingsBinder

    init {
        rootNode = FxmlLoader.load(this, "session-panel.fxml")
        children.add(rootNode)
    }

    /**
     * Wires provider settings, model discovery, toggles, and runtime limit controls.
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
                ollamaSettingsGroup = ollamaSettingsGroup,
                openAiSettingsGroup = openAiSettingsGroup,
                modelInfoLabel = modelInfoLabel,
                providerCatalog = providerCatalog,
            )
        refreshProviderChoices()
        providerSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            if (selected != null) {
                val provider = providerCatalog.enabledProviders().firstOrNull { it.name == selected } ?: return@addListener
                providerCatalog.setActiveProvider(provider.id)
                AppConfig.instance.save()
                providerSettingsBinder.showActiveProvider()
                modelController.updateProviderSpecificControls()
                modelController.refreshModels()
            }
        }
        providerSettingsBinder =
            SessionProviderSettingsBinder(
                ollamaApiKeyField,
                ollamaBaseUrlField,
                openAiApiKeyField,
                openAiBaseUrlField,
                providerCatalog,
            ).also(SessionProviderSettingsBinder::bind)
        addProviderButton.setOnAction {
            ProviderProfileDialog.show { profile ->
                providerCatalog.saveProvider(profile)
                refreshProviderChoices(profile.id)
            }
        }
        editProviderButton.setOnAction {
            providerCatalog.getProvider(providerCatalog.activeProviderId())?.let { profile ->
                ProviderProfileDialog.show(profile) { updated ->
                    providerCatalog.saveProvider(updated)
                    refreshProviderChoices(updated.id)
                    providerSettingsBinder.showActiveProvider()
                }
            }
        }
        deleteProviderButton.setOnAction {
            if (providerCatalog.deleteProvider(providerCatalog.activeProviderId())) {
                refreshProviderChoices()
                providerSettingsBinder.showActiveProvider()
                modelController.refreshModels()
            }
        }
        contextSlider.valueProperty().addListener { _, _, newVal ->
            contextValueLabel.text = newVal.toInt().toString()
            AppConfig.instance.contextLength = newVal.toInt()
            AppConfig.instance.save()
        }
        modelSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            if (selected != null) {
                val providerId = providerCatalog.activeProviderId()
                providerCatalog.getProvider(providerId)?.let { profile ->
                    providerCatalog.saveProvider(profile.copy(defaultModel = selected))
                }
                when (providerId) {
                    "ollama", "openai" -> AppConfig.instance.setActiveModel(selected)
                }
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
        val activeProvider = providerCatalog.getProvider(providerCatalog.activeProviderId())
        providerSelector.selectionModel.select(activeProvider?.name)
        modelController.updateProviderSpecificControls()
        modelController.updateFavoriteButton(modelSelector.selectionModel.selectedItem)
    }

    private fun refreshProviderChoices(selectProviderId: String = providerCatalog.activeProviderId()) {
        val providers = providerCatalog.enabledProviders()
        providerSelector.items.setAll(providers.map { it.name })
        providerSelector.selectionModel.select(providers.firstOrNull { it.id == selectProviderId }?.name)
        deleteProviderButton.isDisable = providers.size <= 1
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
