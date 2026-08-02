package de.heckenmann.visualagent.agent

import kotlinx.serialization.Serializable

/**
 * Runtime and model configuration for a sub-agent.
 *
 * The provider/model fields are optional overrides. Blank or null values inherit
 * the active session provider and model at request time.
 *
 * @property timeout Maximum execution time in seconds for one task
 * @property maxRetries Number of retries allowed for recoverable failures
 * @property memoryLimitMb Soft memory budget shown in agent configuration
 * @property provider Optional provider profile override
 * @property model Optional model override within the selected provider
 * @property temperature Optional sampling temperature override
 * @property topP Optional nucleus sampling override
 * @property maxTokens Optional maximum generated token count
 * @property variant Optional model variant name configured in the provider catalog
 * @property options Provider-specific request options merged at execution time
 * @property tools Optional per-agent tool override; null keeps template/default tool selection
 * @property templateName Optional template key used to initialize this config and resolve tools
 */
@Serializable
data class AgentConfig(
    val timeout: Int = 60,
    val maxRetries: Int = 3,
    val memoryLimitMb: Long = 512,
    val provider: String? = null,
    val model: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val variant: String? = null,
    val options: Map<String, String> = emptyMap(),
    val tools: List<String>? = null,
    val templateName: String? = null,
) {
    /**
     * Converts the agent-specific model overrides into a provider request selection.
     *
     * Empty provider and model values inherit the active session configuration.
     *
     * @return Request-scoped model selection
     */
    fun modelSelection(): ModelSelection =
        ModelSelection(
            provider = provider?.trim()?.takeIf(String::isNotEmpty),
            model = model?.trim()?.takeIf(String::isNotEmpty),
            variant = variant?.trim()?.takeIf(String::isNotEmpty),
            parameters =
                ModelParameters(
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                ),
            options = options,
        )

    companion object {
        /**
         * Pre-built templates for common agent types.
         */
        val TEMPLATES =
            mapOf(
                "researcher" to AgentConfig(timeout = 120, maxRetries = 5, memoryLimitMb = 512),
                "coder" to AgentConfig(timeout = 180, maxRetries = 3, memoryLimitMb = 1024),
                "documenter" to AgentConfig(timeout = 90, maxRetries = 2, memoryLimitMb = 256),
                "reviewer" to AgentConfig(timeout = 150, maxRetries = 3, memoryLimitMb = 768),
                "tester" to AgentConfig(timeout = 120, maxRetries = 4, memoryLimitMb = 512),
            )

        /**
         * Returns a predefined configuration or the default configuration when the template is unknown.
         *
         * @param templateName Template key such as `researcher`, `coder`, or `tester`
         * @return Matching sub-agent configuration
         */
        fun fromTemplate(templateName: String): AgentConfig {
            val base = TEMPLATES[templateName] ?: AgentConfig()
            return base.copy(templateName = templateName)
        }
    }
}
