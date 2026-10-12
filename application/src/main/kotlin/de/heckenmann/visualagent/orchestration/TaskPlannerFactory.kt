package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.todo.TodoManager

/** Wires current effective capabilities into the production planner without changing test defaults. */
internal fun configuredTaskPlanner(
    todos: TodoManager,
    agents: Map<String, SubAgent>,
    provider: LLMProvider,
    tools: AgentToolConfigService,
): AutonomousTaskPlanner =
    AutonomousTaskPlanner(
        todoManager = todos,
        subAgents = agents,
        llmProvider = provider,
        agentToolConfigService = tools,
        operationReady = tools::supportsOperation,
        registeredTools = tools::registeredTools,
    )
