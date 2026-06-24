package de.heckenmann.visualagent.ui.panels.tests

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.config.AppThemeStylesheets
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.knowledge.SubAgentConfigStore
import de.heckenmann.visualagent.ui.panels.ApplicationSettingsPanel
import de.heckenmann.visualagent.ui.panels.FxTestSupport
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.DialogPane
import javafx.scene.control.Spinner
import javafx.scene.layout.VBox
import javafx.stage.Window
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ApplicationSettingsPanelInteractionTest {
    @Test
    fun `appearance controls persist changes reset defaults and fill layout`() =
        FxTestSupport.run {
            val previousTheme = AppConfig.instance.theme
            val previousFontSize = AppConfig.instance.fontSize
            val previousContextLoader = Thread.currentThread().contextClassLoader
            try {
                val panel = ApplicationSettingsPanel()
                val theme = panel.field<ComboBox<String>>("themeSelector")
                val fontSize = panel.field<Spinner<Int>>("fontSizeSpinner")

                Thread.currentThread().contextClassLoader = null
                theme.selectionModel.select("Primer Light")
                fontSize.valueFactory.value = 18
                assertEquals("Primer Light", AppConfig.instance.theme)
                assertEquals(AppThemeStylesheets.stylesheetFor("Primer Light"), Application.getUserAgentStylesheet())
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
                assertEquals(AppThemeStylesheets.stylesheetFor("Dracula"), Application.getUserAgentStylesheet())
                assertEquals(14, fontSize.value)

                panel.resize(720.0, 520.0)
                panel.layout()
                assertEquals(720.0, panel.field<VBox>("root").width)
                assertEquals(520.0, panel.field<VBox>("root").height)
            } finally {
                AppConfig.instance.theme = previousTheme
                AppConfig.instance.fontSize = previousFontSize
                AppConfig.instance.save()
                Thread.currentThread().contextClassLoader = previousContextLoader
                runCatching {
                    Application.setUserAgentStylesheet(AppThemeStylesheets.stylesheetFor(previousTheme))
                }
            }
        }

    @Test
    fun `tool toggles persist global tool enablement`() =
        FxTestSupport.run {
            val store = MapSubAgentConfigStore()
            val service = AgentToolConfigService(store)
            val registry = ToolRegistry(listOf(FakeTool("file:read"), FakeTool("terminal")), ToolEventBus())
            val panel = ApplicationSettingsPanel(registry, service)
            val toolToggleBox = panel.field<VBox>("toolToggleBox")

            val fileReadToggle =
                descendants(toolToggleBox)
                    .filterIsInstance<CheckBox>()
                    .first { it.text == "file:read" }

            fileReadToggle.fire()

            assertFalse(service.isToolGloballyEnabled("file:read"))
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> ApplicationSettingsPanel.field(name: String): T {
        val field = ApplicationSettingsPanel::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this) as T
    }

    private fun descendants(root: javafx.scene.Parent): List<javafx.scene.Node> =
        root.childrenUnmodifiable.flatMap { child ->
            listOf(child) + if (child is javafx.scene.Parent) descendants(child) else emptyList()
        }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }

    private class FakeTool(
        id: String,
    ) : VisualAgentTool {
        override val definition =
            ToolDefinition(
                id = ToolId(id),
                name = id.replace(':', '_'),
                description = "Fake $id",
                inputSchema = """{"type":"object"}""",
            )

        override fun execute(
            inputJson: String,
            context: Map<String, Any>,
        ): ToolResult = ToolResult(definition.id.value, true, "ok")
    }

    private class MapSubAgentConfigStore :
        SubAgentConfigStore,
        PreferenceStore {
        private val configs = linkedMapOf<String, SubAgentToolConfig>()
        private val preferences = linkedMapOf<String, String>()

        override fun saveSubAgentConfig(config: SubAgentToolConfig) {
            configs[config.id] = config
        }

        override fun getSubAgentConfig(id: String): SubAgentToolConfig? = configs[id]

        override fun listSubAgentConfigs(): List<SubAgentToolConfig> = configs.values.toList()

        override fun getPreference(key: String): String? = preferences[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            preferences[key] = value
        }
    }
}
