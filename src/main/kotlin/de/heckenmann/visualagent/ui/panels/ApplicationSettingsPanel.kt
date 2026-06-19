package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.fxml.FXML
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Application-level settings panel for appearance, configuration import/export, and reset actions.
 */
@Component
@Lazy
class ApplicationSettingsPanel : Region() {
    @FXML
    private lateinit var root: VBox

    @FXML
    private lateinit var themeSelector: ComboBox<String>

    @FXML
    private lateinit var fontSizeSpinner: Spinner<Int>

    @FXML
    private lateinit var importConfigButton: Button

    @FXML
    private lateinit var exportConfigButton: Button

    @FXML
    private lateinit var resetDefaultsButton: Button

    private val themes =
        listOf(
            "Dracula",
            "Primer Dark",
            "Primer Light",
            "Nord Dark",
            "Nord Light",
            "Cupertino Dark",
            "Cupertino Light",
        )

    init {
        val loaded = FxmlLoader.load(this, "application-settings.fxml")
        children.add(loaded)
    }

    /**
     * Wires controls to persisted application settings.
     */
    @FXML
    fun initialize() {
        themeSelector.items.setAll(themes)
        themeSelector.selectionModel.select(AppConfig.instance.theme)
        themeSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            if (selected != null) {
                AppConfig.instance.theme = selected
                AppConfig.instance.save()
            }
        }

        fontSizeSpinner.valueFactory = IntegerSpinnerValueFactory(10, 24, AppConfig.instance.fontSize)
        fontSizeSpinner.valueFactory.valueProperty().addListener { _, _, newVal ->
            if (newVal != null) {
                AppConfig.instance.fontSize = newVal
                AppConfig.instance.save()
            }
        }

        importConfigButton.setOnAction { importConfig() }
        exportConfigButton.setOnAction { exportConfig() }
        resetDefaultsButton.setOnAction { resetDefaults() }
    }

    private fun exportConfig() {
        val chooser =
            FileChooser().apply {
                title = "Export Visual Agent Config"
                initialFileName = "visual-agent.properties"
            }
        val file = chooser.showSaveDialog(fontSizeSpinner.scene?.window) ?: return
        runCatching { AppConfig.instance.exportTo(file) }
            .onFailure { showFileError("Could not export configuration", it) }
    }

    private fun importConfig() {
        val chooser =
            FileChooser().apply {
                title = "Import Visual Agent Config"
            }
        val file = chooser.showOpenDialog(fontSizeSpinner.scene?.window) ?: return
        runCatching { AppConfig.instance.importFrom(file) }
            .onSuccess { syncControlsFromConfig() }
            .onFailure { showFileError("Could not import configuration", it) }
    }

    private fun resetDefaults() {
        val confirmation =
            Alert(
                Alert.AlertType.CONFIRMATION,
                "Reset appearance settings to their defaults?",
                ButtonType.CANCEL,
                ButtonType.OK,
            ).apply {
                title = "Reset Appearance"
                headerText = "Reset theme and font size"
            }
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return

        AppConfig.instance.theme = "Dracula"
        AppConfig.instance.fontSize = 14
        AppConfig.instance.save()
        syncControlsFromConfig()
    }

    private fun syncControlsFromConfig() {
        themeSelector.selectionModel.select(AppConfig.instance.theme)
        fontSizeSpinner.valueFactory.value = AppConfig.instance.fontSize.coerceIn(10, 24)
    }

    private fun showFileError(
        message: String,
        error: Throwable,
    ) {
        Alert(Alert.AlertType.ERROR)
            .apply {
                title = "Configuration Error"
                headerText = message
                contentText = error.message ?: "The selected file could not be processed."
            }.showAndWait()
    }

    /**
     * Resizes the loaded FXML root to the full panel bounds.
     */
    override fun layoutChildren() {
        root.resizeRelocate(0.0, 0.0, width, height)
    }
}
