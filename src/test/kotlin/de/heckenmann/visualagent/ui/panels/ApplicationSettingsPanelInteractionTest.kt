package de.heckenmann.visualagent.ui.panels.tests

import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.panels.ApplicationSettingsPanel
import de.heckenmann.visualagent.ui.panels.FxTestSupport
import javafx.application.Platform
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.DialogPane
import javafx.scene.control.Spinner
import javafx.scene.layout.VBox
import javafx.stage.Window
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApplicationSettingsPanelInteractionTest {
    @Test
    fun `appearance controls persist changes reset defaults and fill layout`() =
        FxTestSupport.run {
            val previousTheme = AppConfig.instance.theme
            val previousFontSize = AppConfig.instance.fontSize
            try {
                val panel = ApplicationSettingsPanel()
                val theme = panel.field<ComboBox<String>>("themeSelector")
                val fontSize = panel.field<Spinner<Int>>("fontSizeSpinner")

                theme.selectionModel.select("Primer Light")
                fontSize.valueFactory.value = 18
                assertEquals("Primer Light", AppConfig.instance.theme)
                assertEquals(18, AppConfig.instance.fontSize)

                Platform.runLater {
                    val pane =
                        Window
                            .getWindows()
                            .filter { it.isShowing }
                            .mapNotNull { it.scene?.root as? DialogPane }
                            .last()
                    pane.buttonTypes
                        .map { pane.lookupButton(it) }
                        .filterIsInstance<Button>()
                        .first { it.text == "OK" }
                        .fire()
                }
                panel.field<Button>("resetDefaultsButton").fire()
                assertEquals("Dracula", theme.value)
                assertEquals(14, fontSize.value)

                panel.resize(720.0, 520.0)
                panel.layout()
                assertEquals(720.0, panel.field<VBox>("root").width)
                assertEquals(520.0, panel.field<VBox>("root").height)
            } finally {
                AppConfig.instance.theme = previousTheme
                AppConfig.instance.fontSize = previousFontSize
                AppConfig.instance.save()
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> ApplicationSettingsPanel.field(name: String): T {
        val field = ApplicationSettingsPanel::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this) as T
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
