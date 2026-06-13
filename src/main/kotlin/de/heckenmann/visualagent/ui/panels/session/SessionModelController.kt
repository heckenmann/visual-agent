package de.heckenmann.visualagent.ui.panels.session

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.config.AppConfig
import javafx.application.Platform
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KotlinLogging

/**
 * Handles model discovery, filtering, and favorites for the session panel.
 */
internal class SessionModelController(
    private val modelSelector: ComboBox<String>,
    private val modelSearchField: TextField,
    private val favoritesOnlyToggle: CheckBox,
    private val favoriteButton: Button,
    private val refreshModelsButton: Button,
    private val openAiSettingsGroup: VBox,
    private val modelInfoLabel: Label,
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
                    Result.success(withContext(Dispatchers.IO) { client.getModels() })
                } catch (e: Exception) {
                    logger.warn { "refreshModels failed: ${e.message}" }
                    Result.failure(e)
                }
            Platform.runLater {
                val models = result.getOrDefault(emptyList())
                logger.debug { "refreshModels got ${models.size} models: $models" }
                allModels.clear()
                allModels.addAll(models)
                applyModelFilter(selectPreferred = true)
                modelSelector.isDisable = allModels.isEmpty()
                refreshModelsButton.isDisable = false
                result.exceptionOrNull()?.let { error ->
                    modelInfoLabel.text = "Could not load models: ${error.message ?: "Unknown provider error"}"
                }
            }
        }
    }

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

    fun updateProviderSpecificControls() {
        val openAiSelected = AppConfig.instance.normalizedProvider() == "openai"
        openAiSettingsGroup.isVisible = openAiSelected
        openAiSettingsGroup.isManaged = openAiSelected
    }

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

        val currentModel = AppConfig.instance.activeModel()
        when {
            filtered.contains(currentModel) -> modelSelector.selectionModel.select(currentModel)
            selectPreferred -> modelSelector.selectionModel.select(0)
            modelSelector.selectionModel.selectedIndex < 0 -> modelSelector.selectionModel.select(0)
        }

        updateFavoriteButton(modelSelector.selectionModel.selectedItem)
    }

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
        favoriteButton.text = if (isFavorite) "★" else "☆"
        favoriteButton.isDisable = selected == null
        favoriteButton.tooltip = javafx.scene.control.Tooltip(if (isFavorite) "Remove from favorites" else "Add to favorites")
    }
}
