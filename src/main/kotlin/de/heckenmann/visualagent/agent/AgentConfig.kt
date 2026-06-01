package de.heckenmann.visualagent.agent

import kotlinx.serialization.Serializable

/**
 * Configuration settings for a SubAgent.
 * Controls behavior like timeouts, retry policies, and resource limits.
 */
@Serializable
/**
 * Represents AgentConfig.
 */
data class AgentConfig(
    val timeout: Int = 60,
    val maxRetries: Int = 3,
    val memoryLimitMb: Long = 512,
) {
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
         * Executes fromTemplate.
         */
        fun fromTemplate(templateName: String): AgentConfig = TEMPLATES[templateName] ?: AgentConfig()
    }
}
