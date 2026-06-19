package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.provider.ProviderProfile

/**
 * Pure helper functions used by [AgentDetailsDialog].
 */
internal object AgentDetailsDialogSupport {
    const val PROVIDER_DEFAULT = "Session default"
    const val PROVIDER_OLLAMA = "Ollama"
    const val PROVIDER_OPENAI = "OpenAI"

    fun templateFor(agent: SubAgent?): String {
        val config = agent?.config ?: return AgentConfig.TEMPLATES.keys.first()
        return AgentConfig.TEMPLATES.entries
            .firstOrNull { (_, template) ->
                template.timeout == config.timeout &&
                    template.maxRetries == config.maxRetries &&
                    template.memoryLimitMb == config.memoryLimitMb
            }?.key
            ?: AgentConfig.TEMPLATES.keys.first()
    }

    fun templateLabel(key: String): String = key.replaceFirstChar(Char::uppercase)

    fun templateKey(label: String): String = label.lowercase()

    fun providerLabel(
        provider: String?,
        providers: List<ProviderProfile>,
    ): String =
        providers.firstOrNull { it.id == provider }?.name
            ?: when (provider?.lowercase()) {
                "ollama" -> PROVIDER_OLLAMA
                "openai" -> PROVIDER_OPENAI
                else -> PROVIDER_DEFAULT
            }

    fun providerKey(
        label: String,
        providers: List<ProviderProfile>,
    ): String? =
        providers.firstOrNull { it.name == label }?.id
            ?: when (label) {
                PROVIDER_OLLAMA -> "ollama"
                PROVIDER_OPENAI -> "openai"
                else -> null
            }

    fun validationError(
        name: String,
        role: String,
        temperature: String,
        topP: String,
        maxTokens: String,
    ): String? {
        if (name.isBlank() || role.isBlank()) return "Name and role are required."
        if (temperature.isNotBlank() && temperature.toDoubleOrNull()?.let { it !in 0.0..2.0 } != false) {
            return "Temperature must be between 0.0 and 2.0."
        }
        if (topP.isNotBlank() && topP.toDoubleOrNull()?.let { it !in 0.0..1.0 } != false) {
            return "Top P must be between 0.0 and 1.0."
        }
        if (maxTokens.isNotBlank() && maxTokens.toIntOrNull()?.let { it <= 0 } != false) {
            return "Max tokens must be a positive integer."
        }
        return null
    }

    fun formatOptions(options: Map<String, String>): String = options.entries.joinToString("\n") { (key, value) -> "$key=$value" }

    fun parseOptions(value: String): Map<String, String> =
        value
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it.contains('=') }
            .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
}
