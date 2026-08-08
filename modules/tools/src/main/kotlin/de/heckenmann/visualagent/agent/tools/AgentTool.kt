package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component

/**
 * Marks a provider-neutral tool as a Spring-discovered model tool.
 *
 * Runtime model metadata remains on [VisualAgentTool.definition]. This annotation only
 * controls component discovery and dependency injection.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
annotation class AgentTool
