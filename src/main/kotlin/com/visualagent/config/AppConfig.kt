package com.visualagent.config

import java.io.File
import java.io.FileInputStream
import java.util.Properties

class AppConfig private constructor() {

    var ollamaLocalUrl: String = "http://localhost:11434"
    var ollamaModel: String = "llama3.2"
    var databasePath: String = "./data/visual-agent.db"
    var theme: String = "dark"
    var fontSize: Int = 14
    var browserDefault: String = "firefox"

    companion object {
        val instance: AppConfig by lazy { AppConfig().load() }
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
