package de.heckenmann.visualagent.todo

import de.heckenmann.visualagent.agent.AgentManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Represents TodoConfiguration.
 */
@Configuration
class TodoConfiguration {
    /**
     * Executes todoManager.
     */
    @Bean
    fun todoManager(agentManager: AgentManager): TodoManager = agentManager.todoManager
}
