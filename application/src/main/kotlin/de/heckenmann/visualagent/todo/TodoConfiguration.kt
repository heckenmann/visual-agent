package de.heckenmann.visualagent.todo

import de.heckenmann.visualagent.agent.AgentManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring wiring that exposes the AgentManager-owned todo manager as a bean.
 */
@Configuration
class TodoConfiguration {
    /**
     * Returns the shared todo manager so UI panels and services observe the same state.
     *
     * @param agentManager Main orchestration service that owns todo persistence callbacks
     * @return Shared todo manager instance
     */
    @Bean
    fun todoManager(agentManager: AgentManager): TodoManager = agentManager.todoManager
}
