package de.heckenmann.visualagent.config

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Application configuration singleton.
 *
 * Loads configuration from `src/main/resources/config/app.properties`.
 * Provides access to Ollama settings, database path, UI preferences, and browser configuration.
 *
 * @property ollamaLocalUrl Ollama API endpoint (default: http://localhost:11434)
 * @property ollamaModel Default model name (default: llava)
 * @property databasePath Path to SQLite database file
 * @property theme UI theme (default: Dracula)
 * @property fontSize UI font size (default: 14)
 * @property browserDefault Default browser for web integration (default: firefox)
 */
class AppConfig private constructor() {

    var ollamaLocalUrl: String = "http://localhost:11434"
    // Default to a more commonly available model name; can be overridden in app.properties
    var ollamaModel: String = "llava"
    var databasePath: String = "./data/visual-agent.db"
    var theme: String = "Dracula"
    var fontSize: Int = 14
    var browserDefault: String = "firefox"

    companion object {
        /** Singleton instance of AppConfig */
        val instance: AppConfig by lazy { AppConfig().load() }
    }

    /**
     * Gets the AtlantaFX theme stylesheet for the current theme setting.
     *
     * @return The fully qualified path to the theme stylesheet, or Dracula as default
     */
    fun getThemeStylesheet(): String {
        return when (theme) {
            "Dracula" -> atlantafx.base.theme.Dracula().getUserAgentStylesheet()
            "Primer Dark" -> atlantafx.base.theme.PrimerDark().getUserAgentStylesheet()
            "Primer Light" -> atlantafx.base.theme.PrimerLight().getUserAgentStylesheet()
            "Nord Dark" -> atlantafx.base.theme.NordDark().getUserAgentStylesheet()
            "Nord Light" -> atlantafx.base.theme.NordLight().getUserAgentStylesheet()
            "Cupertino Dark" -> atlantafx.base.theme.CupertinoDark().getUserAgentStylesheet()
            "Cupertino Light" -> atlantafx.base.theme.CupertinoLight().getUserAgentStylesheet()
            else -> atlantafx.base.theme.Dracula().getUserAgentStylesheet()
        }
    }

    /**
     * Saves current configuration to app.properties file.
     * Called when user changes settings in the application.
     */
    fun save() {
        val configFile = File("src/main/resources/config/app.properties")
        val props = Properties()
        props.setProperty("ollama.local.url", ollamaLocalUrl)
        props.setProperty("ollama.model", ollamaModel)
        props.setProperty("database.path", databasePath)
        props.setProperty("ui.theme", theme)
        props.setProperty("ui.font.size", fontSize.toString())
        props.setProperty("browser.default", browserDefault)
        FileOutputStream(configFile).use { fos ->
            props.store(fos, "Visual Agent Configuration")
        }
    }

    private fun load(): AppConfig {
        val configFile = File("src/main/resources/config/app.properties")

        if (configFile.exists()) {
            val props = Properties()
            FileInputStream(configFile).use { fis ->
                props.load(fis)
            }

            ollamaLocalUrl = props.getProperty("ollama.local.url", ollamaLocalUrl)
            ollamaModel = props.getProperty("ollama.model", ollamaModel)
            databasePath = props.getProperty("database.path", databasePath)
            theme = props.getProperty("ui.theme", theme)
            fontSize = props.getProperty("ui.font.size", fontSize.toString()).toIntOrNull() ?: fontSize
            browserDefault = props.getProperty("browser.default", browserDefault)
        }

        return this
    }
}
