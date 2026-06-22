package de.heckenmann.visualagent.ui.panels.session

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import de.heckenmann.visualagent.config.AppConfig
import javafx.application.Platform
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon

/**
 * Handles model discovery, filtering, and favorites for the session panel.
 *
 * Use cases: UC-0000009, UC-0000064, UC-0000065.
 */
internal class SessionModelController(
    private val modelSelector: ComboBox<String>,
    private val modelSearchField: TextField,
    private val favoritesOnlyToggle: CheckBox,
    private val favoriteButton: Button,
    private val refreshModelsButton: Button,
    private val ollamaSettingsGroup: VBox,
    private val openAiSettingsGroup: VBox,
    private val modelInfoLabel: Label,
    private val providerCatalog: ProviderCatalogService,
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var llmProvider: LLMProvider? = null
    private val allModels = mutableListOf<String>()
    private val favoriteModels = linkedSetOf<String>()

    init {
        if (AppConfig.instance.favoriteModels.isNotBlank()) {
            favoriteModels.addAll(
                AppConfig.instance.favoriteModels
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() },
            )
        }
    }

    fun setLlmProvider(client: LLMProvider) {
        llmProvider = client
        refreshModels()
    }

    fun refreshModels() {
        val client = llmProvider ?: return
        modelSelector.isDisable = true
        refreshModelsButton.isDisable = true
        modelInfoLabel.text = "Loading models..."
        scope.launch {
            val result =
                try {
                    Result.success(
                        withContext(Dispatchers.IO) {
                            client.getModels(providerCatalog.activeProviderId())
                        },
                    )
                } catch (e: Exception) {
                    logger.warn { "refreshModels failed: ${e.message}" }
                    Result.failure(e)
                }
            Platform.runLater {
                val models =
                    result.getOrElse {
                        providerCatalog
                            .selectableModels(providerCatalog.activeProviderId())
                            .map { model -> model.id }
                    }
                logger.debug { "refreshModels got ${models.size} models: $models" }
                allModels.clear()
                allModels.addAll(models)
                applyModelFilter(selectPreferred = true)
                modelSelector.isDisable = allModels.isEmpty()
                refreshModelsButton.isDisable = false
                result.exceptionOrNull()?.let { error ->
                    modelInfoLabel.text = ProviderErrorMessages.userFacing(error)
                }
            }
        }
    }

    /** Loads and displays provider-specific details for one selected model. Use cases: UC-0000065. */
    fun refreshModelDetails(modelName: String) {
        val client = llmProvider ?: return
        modelInfoLabel.text = "Loading model details..."
        scope.launch {
            try {
                val details =
                    withContext(Dispatchers.IO) {
                        client.getModelDetails(providerCatalog.activeProviderId(), modelName)
                    }
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
                    modelInfoLabel.text = ProviderErrorMessages.userFacing(e)
                }
            }
        }
    }

    fun updateProviderSpecificControls() {
        val adapter = providerCatalog.getProvider(providerCatalog.activeProviderId())?.adapter
        val ollamaSelected = adapter == ProviderAdapter.OLLAMA
        ollamaSettingsGroup.isVisible = ollamaSelected
        ollamaSettingsGroup.isManaged = ollamaSelected
        openAiSettingsGroup.isVisible = !ollamaSelected
        openAiSettingsGroup.isManaged = !ollamaSelected
    }

    /** Applies search and favorites-only filtering to the selectable model list. Use cases: UC-0000064. */
    fun applyModelFilter(selectPreferred: Boolean = false) {
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
                    "No models available. Check the provider connection and credentials."
                } else {
                    "No models match the current filter."
                }
            return
        }

        val currentModel = providerCatalog.getProvider(providerCatalog.activeProviderId())?.defaultModel.orEmpty()
        when {
            filtered.contains(currentModel) -> modelSelector.selectionModel.select(currentModel)
            selectPreferred -> modelSelector.selectionModel.select(0)
            modelSelector.selectionModel.selectedIndex < 0 -> modelSelector.selectionModel.select(0)
        }

        updateFavoriteButton(modelSelector.selectionModel.selectedItem)
    }

    /** Toggles and persists favorite state for the currently selected model. Use cases: UC-0000064. */
    fun toggleFavoriteForSelectedModel() {
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

    fun updateFavoriteButton(selected: String?) {
        val isFavorite = selected != null && favoriteModels.contains(selected)
        favoriteButton.text = null
        (favoriteButton.graphic as? FontIcon)?.iconCode = FontAwesomeSolid.STAR
        favoriteButton.opacity = if (isFavorite) 1.0 else 0.55
        favoriteButton.isDisable = selected == null
        favoriteButton.tooltip = Tooltip(if (isFavorite) "Remove from favorites" else "Add to favorites")
    }
}
