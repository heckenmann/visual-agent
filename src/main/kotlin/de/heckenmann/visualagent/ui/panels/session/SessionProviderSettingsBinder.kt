package de.heckenmann.visualagent.ui.panels.session

import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.config.AppConfig
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField

internal class SessionProviderSettingsBinder(
    private val ollamaApiKeyField: PasswordField,
    private val ollamaBaseUrlField: TextField,
    private val openAiApiKeyField: PasswordField,
    private val openAiBaseUrlField: TextField,
    private val providerCatalog: ProviderCatalogService,
) {
    private var updating = false

    fun bind() {
        ollamaApiKeyField.textProperty().addListener { _, _, value ->
            if (!updating) updateActive(apiKey = value ?: "")
        }
        ollamaBaseUrlField.textProperty().addListener { _, _, value ->
            if (!updating) updateActive(baseUrl = (value ?: "").ifBlank { "http://localhost:11434" })
        }
        openAiApiKeyField.textProperty().addListener { _, _, value ->
            if (!updating) updateActive(apiKey = value ?: "")
        }
        openAiBaseUrlField.textProperty().addListener { _, _, value ->
            if (!updating) updateActive(baseUrl = (value ?: "").ifBlank { "https://api.openai.com" })
        }
        showActiveProvider()
    }

    fun showActiveProvider() {
        val profile = providerCatalog.getProvider(providerCatalog.activeProviderId()) ?: return
        updating = true
        ollamaApiKeyField.text = profile.apiKey
        ollamaBaseUrlField.text = profile.baseUrl
        openAiApiKeyField.text = profile.apiKey
        openAiBaseUrlField.text = profile.baseUrl
        updating = false
    }

    private fun updateActive(
        baseUrl: String? = null,
        apiKey: String? = null,
    ) {
        val profile = providerCatalog.getProvider(providerCatalog.activeProviderId()) ?: return
        val updated =
            profile.copy(
                baseUrl = baseUrl ?: profile.baseUrl,
                apiKey = apiKey ?: profile.apiKey,
            )
        providerCatalog.saveProvider(updated)
        when (profile.id) {
            "ollama" -> {
                AppConfig.instance.ollamaLocalUrl = updated.baseUrl
                AppConfig.instance.ollamaApiKey = updated.apiKey
            }
            "openai" -> {
                AppConfig.instance.openAiBaseUrl = updated.baseUrl
                AppConfig.instance.openAiApiKey = updated.apiKey
            }
        }
    }
}
