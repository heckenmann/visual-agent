package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

class ApplicationSettingsPanel : Region() {

    @FXML
    private lateinit var root: VBox

    @FXML
    private lateinit var themeSelector: ComboBox<String>

    @FXML
    private lateinit var fontSizeSpinner: Spinner<Int>

    private val themes = listOf(
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

    @FXML
    fun initialize() {
        themeSelector.items.setAll(themes)
        themeSelector.selectionModel.select(AppConfig.instance.theme)
        themeSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            if (selected != null) {
                AppConfig.instance.theme = selected
                AppConfig.instance.save()
                reloadTheme()
            }
        }

        fontSizeSpinner.valueFactory = IntegerSpinnerValueFactory(10, 24, AppConfig.instance.fontSize)
        fontSizeSpinner.valueFactory.valueProperty().addListener { _, _, newVal ->
            if (newVal != null) {
                AppConfig.instance.fontSize = newVal
                AppConfig.instance.save()
                applyFontSize(newVal)
            }
        }
    }

    private fun reloadTheme() {
        Platform.runLater {
            Application.setUserAgentStylesheet(AppConfig.instance.getThemeStylesheet())
        }
    }

    private fun applyFontSize(size: Int) {
        val currentScene = scene ?: return
        Platform.runLater {
            currentScene.root.style = "-fx-font-size: ${size}px;"
        }
    }
}
