package de.heckenmann.visualagent.ui.panels.session

import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.VBox

/**
 * Creates or edits one dynamic provider profile.
 */
internal object ProviderProfileDialog {
    fun show(
        existing: ProviderProfile? = null,
        onSave: (ProviderProfile) -> Unit,
    ) {
        val dialog =
            Dialog<Unit>().apply {
                title = if (existing == null) "Add Provider" else "Edit Provider"
                headerText = "Configure a model provider profile"
            }
        val saveType = ButtonType("Save provider", ButtonBar.ButtonData.OK_DONE)
        dialog.dialogPane.buttonTypes.addAll(saveType, ButtonType.CANCEL)

        val idField = TextField(existing?.id.orEmpty()).apply { promptText = "example: company-openai" }
        val nameField = TextField(existing?.name.orEmpty()).apply { promptText = "Display name" }
        val adapter =
            ComboBox<ProviderAdapter>().apply {
                items.setAll(ProviderAdapter.entries)
                selectionModel.select(existing?.adapter ?: ProviderAdapter.OPENAI_COMPATIBLE)
            }
        val baseUrl = TextField(existing?.baseUrl.orEmpty()).apply { promptText = "https://api.example.com" }
        val apiKey = PasswordField().apply { text = existing?.apiKey.orEmpty() }
        val defaultModel = TextField(existing?.defaultModel.orEmpty()).apply { promptText = "Default model ID" }
        val options =
            TextArea(formatMap(existing?.options.orEmpty())).apply {
                promptText = "timeout=300000\nreasoningEffort=medium"
                prefRowCount = 3
            }
        val models =
            TextArea(formatModels(existing?.models.orEmpty())).apply {
                promptText = "model-id|ACTIVE|contextLimit|outputLimit|temperature=0.2;topP=0.9"
                prefRowCount = 5
            }
        val whitelist =
            TextField(existing?.modelWhitelist?.joinToString(",").orEmpty()).apply {
                promptText = "Optional comma-separated model IDs"
            }
        val blacklist =
            TextField(existing?.modelBlacklist?.joinToString(",").orEmpty()).apply {
                promptText = "Optional comma-separated model IDs"
            }
        val validation = Label().apply { styleClass.add("dialog-validation") }
        dialog.dialogPane.content =
            VBox(
                10.0,
                field("Provider ID", idField),
                field("Name", nameField),
                field("Adapter", adapter),
                field("Base URL", baseUrl),
                field("API key", apiKey),
                field("Default model", defaultModel),
                field("Provider options", options),
                field("Models", models),
                field("Model whitelist", whitelist),
                field("Model blacklist", blacklist),
                validation,
            )
        val saveButton = dialog.dialogPane.lookupButton(saveType) as Button

        fun validate() {
            val error =
                when {
                    idField.text.isBlank() -> "Provider ID is required."
                    !idField.text.matches(Regex("[a-zA-Z0-9._-]+")) -> "Provider ID contains invalid characters."
                    nameField.text.isBlank() -> "Name is required."
                    baseUrl.text.isBlank() -> "Base URL is required."
                    else -> null
                }
            validation.text = error.orEmpty()
            validation.isVisible = error != null
            validation.isManaged = error != null
            saveButton.isDisable = error != null
        }
        listOf(idField, nameField, baseUrl).forEach { field ->
            field.textProperty().addListener { _, _, _ -> validate() }
        }
        idField.isDisable = existing != null
        validate()
        dialog.setResultConverter { button ->
            if (button === saveType) {
                onSave(
                    ProviderProfile(
                        id = idField.text.trim(),
                        name = nameField.text.trim(),
                        adapter = adapter.value,
                        baseUrl = baseUrl.text.trim(),
                        apiKey = apiKey.text,
                        enabled = existing?.enabled ?: true,
                        defaultModel = defaultModel.text.trim(),
                        options = parseMap(options.text),
                        models = parseModels(models.text),
                        modelWhitelist = parseSet(whitelist.text),
                        modelBlacklist = parseSet(blacklist.text),
                    ),
                )
            }
            null
        }
        dialog.showAndWait()
    }

    private fun field(
        label: String,
        control: javafx.scene.Node,
    ): VBox = VBox(4.0, Label(label).apply { styleClass.add("field-label") }, control)

    private fun formatMap(values: Map<String, String>): String = values.entries.joinToString("\n") { (key, value) -> "$key=$value" }

    private fun parseMap(value: String): Map<String, String> =
        value
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it.contains('=') }
            .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }

    private fun parseSet(value: String): Set<String> =
        value
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun formatModels(models: List<ProviderModelConfig>): String =
        models.joinToString("\n") { model ->
            listOf(
                model.id,
                model.status.name,
                model.contextLimit?.toString().orEmpty(),
                model.outputLimit?.toString().orEmpty(),
                model.options.entries.joinToString(";") { (key, value) -> "$key=$value" },
            ).joinToString("|")
        }

    private fun parseModels(value: String): List<ProviderModelConfig> =
        value
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { line ->
                val parts = line.split('|', limit = 5)
                ProviderModelConfig(
                    id = parts[0].trim(),
                    status =
                        parts.getOrNull(1)?.let { runCatching { ModelStatus.valueOf(it.trim().uppercase()) }.getOrNull() }
                            ?: ModelStatus.ACTIVE,
                    contextLimit = parts.getOrNull(2)?.trim()?.toIntOrNull(),
                    outputLimit = parts.getOrNull(3)?.trim()?.toIntOrNull(),
                    options =
                        parts
                            .getOrNull(4)
                            .orEmpty()
                            .split(';')
                            .map(String::trim)
                            .filter { it.contains('=') }
                            .associate { option ->
                                option.substringBefore('=').trim() to option.substringAfter('=').trim()
                            },
                )
            }.toList()
}
