package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files

/**
 * Represents ApplicationSettingsPanel.
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
     * Executes initialize.
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
        Files.copy(
            File("src/main/resources/config/app.properties").toPath(),
            file.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun importConfig() {
        val chooser =
            FileChooser().apply {
                title = "Import Visual Agent Config"
            }
        val file = chooser.showOpenDialog(fontSizeSpinner.scene?.window) ?: return
        Files.copy(
            file.toPath(),
            File("src/main/resources/config/app.properties").toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
        AppConfig.instance.reload()
    }

    private fun resetDefaults() {
        AppConfig.instance.theme = "Dracula"
        AppConfig.instance.fontSize = 14
        AppConfig.instance.save()
    }
}
