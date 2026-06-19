package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.PROVIDER_DEFAULT
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.PROVIDER_OLLAMA
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.PROVIDER_OPENAI
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.formatOptions
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.parseOptions
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.providerKey
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.providerLabel
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.templateFor
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.templateKey
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.templateLabel
import de.heckenmann.visualagent.ui.panels.AgentDetailsDialogSupport.validationError
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox

/**
 * Creates or edits a sub-agent with validated identity and template fields.
 */
class AgentDetailsDialog {
    companion object {
        /**
         * Opens the agent editor and invokes [onSave] after valid input is confirmed.
         */
        fun showFor(
            agent: SubAgent? = null,
            providers: List<ProviderProfile> = emptyList(),
            onSave: ((String, String, AgentConfig) -> Unit)? = null,
        ) {
            val dialog =
                Dialog<Unit>().apply {
                    title = if (agent == null) "Create Agent" else "Edit Agent"
                    headerText = if (agent == null) "Add a specialized worker" else "Update ${agent.name}"
                    dialogPane.styleClass.add("agent-dialog")
                }
            val saveButtonType = ButtonType(if (agent == null) "Create agent" else "Save changes", ButtonBar.ButtonData.OK_DONE)
            dialog.dialogPane.buttonTypes.addAll(saveButtonType, ButtonType.CANCEL)

            val nameField =
                TextField(agent?.name.orEmpty()).apply {
                    promptText = "Example: Researcher"
                }
            val roleField =
                TextArea(agent?.role.orEmpty()).apply {
                    promptText = "Describe the agent's responsibility and boundaries."
                    isWrapText = true
                    prefRowCount = 3
                }
            val templateChoice =
                ComboBox<String>().apply {
                    items.addAll(AgentConfig.TEMPLATES.keys.map(::templateLabel))
                    maxWidth = Double.MAX_VALUE
                    selectionModel.select(templateLabel(templateFor(agent)))
                }
            val providerChoice =
                ComboBox<String>().apply {
                    items.add(PROVIDER_DEFAULT)
                    items.addAll(providers.map(ProviderProfile::name))
                    if (providers.isEmpty()) items.addAll(PROVIDER_OLLAMA, PROVIDER_OPENAI)
                    maxWidth = Double.MAX_VALUE
                    selectionModel.select(providerLabel(agent?.config?.provider, providers))
                }
            val modelField =
                ComboBox<String>().apply {
                    isEditable = true
                    maxWidth = Double.MAX_VALUE
                    promptText = "Inherit the provider's session model"
                    editor.text = agent?.config?.model.orEmpty()
                }
            val updateModels = {
                val providerId = providerKey(providerChoice.value, providers)
                val profile = providers.firstOrNull { it.id == providerId }
                modelField.items.setAll(
                    profile
                        ?.models
                        ?.filter { it.status.name !in setOf("DEPRECATED", "DISABLED") }
                        ?.map { it.id }
                        .orEmpty(),
                )
            }
            providerChoice.selectionModel.selectedItemProperty().addListener { _, _, _ -> updateModels() }
            updateModels()
            val temperatureField =
                TextField(
                    agent
                        ?.config
                        ?.temperature
                        ?.toString()
                        .orEmpty(),
                ).apply {
                    promptText = "Default"
                }
            val topPField =
                TextField(
                    agent
                        ?.config
                        ?.topP
                        ?.toString()
                        .orEmpty(),
                ).apply {
                    promptText = "Default"
                }
            val maxTokensField =
                TextField(
                    agent
                        ?.config
                        ?.maxTokens
                        ?.toString()
                        .orEmpty(),
                ).apply {
                    promptText = "Default"
                }
            val variantChoice =
                ComboBox<String>().apply {
                    isEditable = true
                    maxWidth = Double.MAX_VALUE
                    editor.text = agent?.config?.variant.orEmpty()
                    promptText = "Optional model variant"
                }
            val optionsArea =
                TextArea(formatOptions(agent?.config?.options.orEmpty())).apply {
                    promptText = "topK=40\nseed=42\nreasoningEffort=medium"
                    isWrapText = false
                    prefRowCount = 3
                }
            val validationLabel =
                Label().apply {
                    styleClass.add("dialog-validation")
                    isVisible = false
                    isManaged = false
                }

            dialog.dialogPane.content =
                VBox(
                    14.0,
                    fieldGroup("Agent name", "Shown in the workspace and activity log.", nameField),
                    fieldGroup("Role and responsibility", "Used as context when assigning work.", roleField),
                    fieldGroup("Execution template", "Provides sensible timeout and resource defaults.", templateChoice),
                    fieldGroup("Provider", "Inherit the session provider or select an agent-specific backend.", providerChoice),
                    fieldGroup("Model", "Enter a model available from the selected provider.", modelField),
                    modelParametersGrid(temperatureField, topPField, maxTokensField),
                    fieldGroup("Variant", "Optional provider/model variant.", variantChoice),
                    fieldGroup("Additional options", "One provider-specific key=value option per line.", optionsArea),
                    validationLabel,
                ).apply { styleClass.add("dialog-form") }

            val saveButton = dialog.dialogPane.lookupButton(saveButtonType) as Button

            val updateValidation = {
                val error =
                    validationError(
                        nameField.text,
                        roleField.text,
                        temperatureField.text,
                        topPField.text,
                        maxTokensField.text,
                    )
                saveButton.isDisable = error != null
                validationLabel.text = error.orEmpty()
                validationLabel.isVisible = error != null
                validationLabel.isManaged = error != null
            }
            nameField.textProperty().addListener { _, _, _ -> updateValidation() }
            roleField.textProperty().addListener { _, _, _ -> updateValidation() }
            temperatureField.textProperty().addListener { _, _, _ -> updateValidation() }
            topPField.textProperty().addListener { _, _, _ -> updateValidation() }
            maxTokensField.textProperty().addListener { _, _, _ -> updateValidation() }
            updateValidation()

            dialog.setResultConverter { button ->
                if (button === saveButtonType) {
                    val template = AgentConfig.fromTemplate(templateKey(templateChoice.value))
                    onSave?.invoke(
                        nameField.text.trim(),
                        roleField.text.trim(),
                        template.copy(
                            provider = providerKey(providerChoice.value, providers),
                            model =
                                modelField.editor.text
                                    .trim()
                                    .takeIf(String::isNotEmpty),
                            temperature = temperatureField.text.trim().toDoubleOrNull(),
                            topP = topPField.text.trim().toDoubleOrNull(),
                            maxTokens = maxTokensField.text.trim().toIntOrNull(),
                            variant =
                                variantChoice.editor.text
                                    .trim()
                                    .takeIf(String::isNotEmpty),
                            options = parseOptions(optionsArea.text),
                        ),
                    )
                }
                null
            }
            dialog.showAndWait()
        }

        private fun fieldGroup(
            title: String,
            description: String,
            control: javafx.scene.Node,
        ): VBox =
            VBox(
                5.0,
                Label(title).apply { styleClass.add("field-label") },
                Label(description).apply { styleClass.add("field-help") },
                control,
            )

        private fun modelParametersGrid(
            temperatureField: TextField,
            topPField: TextField,
            maxTokensField: TextField,
        ): VBox {
            val grid =
                GridPane().apply {
                    hgap = 10.0
                    vgap = 6.0
                    add(Label("Temperature").apply { styleClass.add("field-label") }, 0, 0)
                    add(Label("Top P").apply { styleClass.add("field-label") }, 1, 0)
                    add(Label("Max tokens").apply { styleClass.add("field-label") }, 2, 0)
                    add(temperatureField, 0, 1)
                    add(topPField, 1, 1)
                    add(maxTokensField, 2, 1)
                }
            return VBox(
                5.0,
                Label("Model parameters").apply { styleClass.add("field-label") },
                Label("Leave values empty to use provider defaults.").apply { styleClass.add("field-help") },
                grid,
            )
        }
    }
}
