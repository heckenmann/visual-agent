package de.heckenmann.visualagent.todo

import de.heckenmann.visualagent.agent.AgentManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Exposes todo-related Spring beans from the central agent runtime.
 */
@Configuration
class TodoConfiguration {
    /**
     * Provides the shared [TodoManager] used by UI and agent orchestration.
     *
     * @param agentManager Central agent manager instance
     * @return Shared todo manager
     */
    @Bean
    fun todoManager(agentManager: AgentManager): TodoManager = agentManager.todoManager
}
