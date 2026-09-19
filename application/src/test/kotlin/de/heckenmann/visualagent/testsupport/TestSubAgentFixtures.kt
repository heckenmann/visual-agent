package de.heckenmann.visualagent.testsupport

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.knowledge.PersistedSubAgent
import de.heckenmann.visualagent.knowledge.SubAgentStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

internal fun seedDefaultTestAgents(store: SubAgentStore) {
    listOf(
        Triple("1", "Researcher", "Web research and information gathering"),
        Triple("2", "Coder", "Code implementation and review"),
        Triple("3", "Documenter", "Documentation writing"),
    ).forEach { (id, name, role) ->
        store.saveAgent(
            PersistedSubAgent(
                id = id,
                name = name,
                role = role,
                status = AgentStatus.IDLE.name,
                currentTask = null,
                parentAgentId = null,
                config = Json.encodeToString(AgentConfig()),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
    }
}
